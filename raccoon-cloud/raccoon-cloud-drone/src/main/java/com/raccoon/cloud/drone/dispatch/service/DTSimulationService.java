package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.algorithm.PpoSimulationModel;
import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskStatusEnum;
import com.raccoon.cloud.drone.dispatch.model.AssignmentResult;
import com.raccoon.cloud.drone.dispatch.model.ConflictInfo;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.SimulationResult;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.util.GeoPathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数字孪生预演服务（步骤 6）。
 * <p>流程：
 * <ol>
 *   <li>调用 {@link PpoSimulationModel} 推理奖励值，判断分配整体质量</li>
 *   <li>逐对比较多机轨迹，检测时空冲突（距离 &lt; {@link DispatchConstants#SAFE_DISTANCE_M}）</li>
 *   <li>对检出的冲突任务执行重排：错峰 reschedule，避免空中相遇</li>
 * </ol>
 *
 * @author raccoon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DTSimulationService {

    private final PpoSimulationModel ppoModel;

    /**
     * 执行孪生预演。
     *
     * @param tasks      已分配任务
     * @param terminals  候选终端
     * @param assignment 分配结果（仅用于读取价格矩阵，可空）
     * @return 预演结果
     */
    public SimulationResult simulate(List<DispatchInspectionTask> tasks,
                                     List<TerminalState> terminals,
                                     AssignmentResult assignment) {
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        if (terminals == null) {
            terminals = new ArrayList<>();
        }
        long start = System.currentTimeMillis();
        SimulationResult result = new SimulationResult();
        result.setPpoReward(ppoModel.infer(tasks, terminals));

        List<ConflictInfo> conflicts = detectConflicts(tasks, terminals);
        result.setConflicts(conflicts);

        if (!conflicts.isEmpty()) {
            result.setAdjusted(resolveConflicts(tasks, conflicts));
            result.setAdjustmentNote("已对 " + conflicts.size() + " 处冲突执行错峰重排");
        }

        result.setPassed(conflicts.isEmpty() || result.isAdjusted());
        result.setElapsedMs(System.currentTimeMillis() - start);
        log.info("[dt-sim] ppoReward={} conflicts={} adjusted={} elapsedMs={}",
                round(result.getPpoReward()), conflicts.size(),
                result.isAdjusted(), result.getElapsedMs());
        return result;
    }

    /**
     * 多机时空冲突检测：
     * <ul>
     *   <li>同终端被分配多任务 → 资源冲突</li>
     *   <li>不同终端航点在 {@link DispatchConstants#SAFE_DISTANCE_M} 米内且时间重叠 → 空间冲突</li>
     * </ul>
     */
    private List<ConflictInfo> detectConflicts(List<DispatchInspectionTask> tasks,
                                               List<TerminalState> terminals) {
        List<ConflictInfo> conflicts = new ArrayList<>();
        // 终端 -> 任务列表
        Map<Long, List<DispatchInspectionTask>> byTerminal = new HashMap<>();
        for (DispatchInspectionTask t : tasks) {
            if (t.getAssignedTerminalId() == null) {
                continue;
            }
            byTerminal.computeIfAbsent(t.getAssignedTerminalId(), k -> new ArrayList<>()).add(t);
        }

        // RESOURCE 冲突
        byTerminal.forEach((tid, list) -> {
            if (list.size() > 1) {
                ConflictInfo c = new ConflictInfo();
                c.setConflictType("RESOURCE");
                c.setTerminalIds(List.of(tid));
                c.setTaskIds(list.stream().map(DispatchInspectionTask::getTaskId).toList());
                c.setDescription("终端 " + tid + " 同时被分配 " + list.size() + " 个任务");
                c.setResolution("RESCHEDULE");
                conflicts.add(c);
            }
        });

        // SPATIAL 冲突
        List<DispatchInspectionTask> assigned = tasks.stream()
                .filter(t -> t.getAssignedTerminalId() != null
                        && !t.getDeviceWaypoints().isEmpty())
                .toList();
        for (int i = 0; i < assigned.size(); i++) {
            for (int j = i + 1; j < assigned.size(); j++) {
                DispatchInspectionTask a = assigned.get(i);
                DispatchInspectionTask b = assigned.get(j);
                if (a.getAssignedTerminalId().equals(b.getAssignedTerminalId())) {
                    continue;
                }
                ConflictInfo c = detectSpatial(a, b);
                if (c != null) {
                    conflicts.add(c);
                }
            }
        }
        return conflicts;
    }

    /** 检测两个任务首段路径是否近距相交。 */
    private ConflictInfo detectSpatial(DispatchInspectionTask a, DispatchInspectionTask b) {
        GeoPoint pa = a.getDeviceWaypoints().get(0);
        GeoPoint pb = b.getDeviceWaypoints().get(0);
        if (pa == null || pb == null) {
            return null;
        }
        double d = GeoPathUtils.segmentLengthMeters(pa, pb);
        if (d > DispatchConstants.SAFE_DISTANCE_M) {
            return null;
        }
        ConflictInfo conflict = new ConflictInfo();
        conflict.setConflictType("SPATIAL");
        conflict.setTerminalIds(List.of(a.getAssignedTerminalId(), b.getAssignedTerminalId()));
        conflict.setTaskIds(List.of(a.getTaskId(), b.getTaskId()));
        conflict.setConflictPoint(pa);
        conflict.setMinDistanceM(d);
        conflict.setResolution("REROUTE");
        conflict.setDescription(String.format(
                "终端 %d/%d 首航点距离仅 %.1fm，低于安全间距 %.1fm",
                a.getAssignedTerminalId(), b.getAssignedTerminalId(),
                d, DispatchConstants.SAFE_DISTANCE_M));
        return conflict;
    }

    /**
     * 对检出的冲突错峰重排：保留高优先级任务，低优先级任务标注 simulationResult 后延后。
     * <p>真实场景下应触发路径重规划；这里仅做时间错峰，避免实现复杂度过大。
     */
    private boolean resolveConflicts(List<DispatchInspectionTask> tasks,
                                     List<ConflictInfo> conflicts) {
        boolean adjusted = false;
        for (ConflictInfo c : conflicts) {
            List<DispatchInspectionTask> related = new ArrayList<>();
            for (String tid : c.getTaskIds()) {
                tasks.stream()
                        .filter(t -> tid.equals(t.getTaskId()))
                        .findFirst()
                        .ifPresent(related::add);
            }
            if (related.size() <= 1) {
                continue;
            }
            related.sort(Comparator.comparingInt((DispatchInspectionTask t) ->
                    -t.getPriority().getWeight()));
            for (int i = 1; i < related.size(); i++) {
                DispatchInspectionTask t = related.get(i);
                t.setStatus(DispatchTaskStatusEnum.SIMULATED);
                t.getExtension().put("simulation:rescheduleSeconds",
                        (int) (DispatchConstants.SIM_TIME_TOLERANCE_SEC * i));
                adjusted = true;
            }
        }
        return adjusted;
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
