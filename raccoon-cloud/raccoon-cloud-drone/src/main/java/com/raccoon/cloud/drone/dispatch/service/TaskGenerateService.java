package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskRequest;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskResponse;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskStatusEnum;
import com.raccoon.cloud.drone.dispatch.model.AssignmentResult;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.DispatchWorkOrder;
import com.raccoon.cloud.drone.dispatch.model.SimulationResult;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度中枢总入口服务（步骤 8）。
 * <p>串联：任务解析 → 终端状态感知 → 优先级评估 → 拍卖分配 → 数字孪生预演 → 全局路径规划 →
 * 作业清单生成 → 下发。任何阶段失败均收敛到 {@link DispatchTaskStatusEnum#FAILED}，并返回结构化错误。
 * <p>端到端时延目标 {@link DispatchConstants#E2E_TIMEOUT_MS}=200ms，超出仅告警不中断。
 *
 * @author raccoon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskGenerateService {

    private final TaskParseService taskParseService;
    private final TerminalStateService terminalStateService;
    private final TaskPriorityService taskPriorityService;
    private final AuctionAssignService auctionAssignService;
    private final DTSimulationService dtSimulationService;
    private final GlobalPathPlanService globalPathPlanService;
    private final TaskDispatchService taskDispatchService;

    /**
     * 调度中枢主流程。
     *
     * @param request 原始请求（非空）
     * @return 调度响应
     */
    public DispatchTaskResponse generate(DispatchTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("调度请求不能为空");
        }
        long start = System.currentTimeMillis();
        DispatchTaskResponse response = new DispatchTaskResponse();

        // —— 1. 任务解析 ——
        DispatchInspectionTask task = taskParseService.parse(request);
        response.setTaskId(task.getTaskId());
        response.setRequestId(task.getRequestId());
        response.setCreateTime(task.getCreateTime());
        response.setTaskTypeCode(task.getTaskType().getCode());

        // —— 2. 终端状态感知 ——
        if (task.getMapId() == null) {
            return finish(response, task, start, "缺少场景地图 ID，无法感知终端");
        }
        List<TerminalState> pool = filterPreferred(
                terminalStateService.listByMap(task.getMapId()),
                request.getPreferredTerminalIds());
        if (pool.isEmpty()) {
            return finish(response, task, start, "场景下无可用终端");
        }

        // —— 4. 优先级评估 —— （步骤 3 即终端感知，已在上一步完成）
        taskPriorityService.evaluate(task, pool);
        task.setStatus(DispatchTaskStatusEnum.PRIORITIZED);
        response.setPriority(task.getPriority());
        response.setPriorityScore(task.getPriorityScore());

        // —— 5. 拍卖分配 ——
        AssignmentResult assignment;
        try {
            assignment = auctionAssignService.assign(List.of(task), pool);
        } catch (IllegalArgumentException e) {
            return finish(response, task, start, e.getMessage());
        }
        if (task.getAssignedTerminalId() == null) {
            response.setAssigned(false);
            return finish(response, task, start,
                    "拍卖未分配到可用终端（已记录价格矩阵 size=" + assignment.getPriceMatrix().size() + "）");
        }
        response.setAssigned(true);
        response.setAssignedTerminalId(task.getAssignedTerminalId());
        response.setBidPrice(task.getBidFinalPrice());

        TerminalState terminal = pool.stream()
                .filter(t -> task.getAssignedTerminalId().equals(t.getTerminalId()))
                .findFirst()
                .orElse(null);
        if (terminal != null) {
            response.setAssignedTerminalName(terminal.getTerminalName());
        }

        // —— 6. 数字孪生预演 ——（按需，单任务关闭后仅做冲突检测）
        if (Boolean.TRUE.equals(request.getEnableSimulation())) {
            SimulationResult sim = dtSimulationService.simulate(List.of(task), pool, assignment);
            task.setSimulationResult(sim);
            task.setStatus(DispatchTaskStatusEnum.SIMULATED);
            response.setConflicts(sim.getConflicts());
            if (!sim.isPassed()) {
                log.warn("[generate] taskId={} 孪生预演未通过 conflicts={}", task.getTaskId(),
                        sim.getConflicts().size());
            }
        }

        // —— 7. 全局路径规划 ——
        try {
            response.setPathPlan(globalPathPlanService.plan(task, terminal));
        } catch (Exception e) {
            log.error("[generate] taskId={} 路径规划失败", task.getTaskId(), e);
            return finish(response, task, start, "路径规划失败: " + e.getMessage());
        }

        // —— 8. 作业清单生成与下发 ——
        DispatchWorkOrder order = taskDispatchService.generateAndDispatch(
                task, terminal, Boolean.TRUE.equals(request.getAutoDispatch()));
        response.setWorkOrder(order);

        // 下发完成后失效该终端缓存（容量变更）
        terminalStateService.evict(task.getAssignedTerminalId());

        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > DispatchConstants.E2E_TIMEOUT_MS) {
            log.warn("[generate] taskId={} 端到端耗时 {}ms 超过目标 {}ms",
                    task.getTaskId(), elapsed, DispatchConstants.E2E_TIMEOUT_MS);
        }
        response.setStatus(task.getStatus());
        response.setTotalElapsedMs(elapsed);
        response.setMessage("OK");
        log.info("[generate] 完成 taskId={} status={} terminalId={} totalElapsedMs={}",
                task.getTaskId(), task.getStatus().getCode(),
                task.getAssignedTerminalId(), elapsed);
        return response;
    }

    /**
     * 提前结束并填充失败信息。
     */
    private DispatchTaskResponse finish(DispatchTaskResponse response,
                                        DispatchInspectionTask task,
                                        long start,
                                        String message) {
        if (task.getStatus() != DispatchTaskStatusEnum.COMPLETED
                && task.getStatus() != DispatchTaskStatusEnum.DISPATCHED) {
            task.setStatus(DispatchTaskStatusEnum.FAILED);
        }
        response.setStatus(task.getStatus());
        response.setTotalElapsedMs(System.currentTimeMillis() - start);
        response.setMessage(message);
        log.warn("[generate] taskId={} 终止: {}", task.getTaskId(), message);
        return response;
    }

    /**
     * 应用调用方限定的优先终端集合（preferredTerminalIds 非空时仅保留集合内终端）。
     */
    private List<TerminalState> filterPreferred(List<TerminalState> all, List<Long> preferred) {
        if (preferred == null || preferred.isEmpty()) {
            return all;
        }
        List<TerminalState> result = new ArrayList<>();
        for (TerminalState s : all) {
            if (preferred.contains(s.getTerminalId())) {
                result.add(s);
            }
        }
        return result;
    }
}
