package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.knowledge.AgentKnowledgeClient;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DocumentChunk;
import com.raccoon.cloud.drone.dispatch.model.knowledge.KnowledgeContext;
import com.raccoon.cloud.drone.dispatch.model.knowledge.PlanningConstraint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 混合 RAG 知识上下文构建服务（步骤 2）。
 * <p>任务分配前并行执行 Milvus 语义检索（规程/手册）与 Neo4j 图查询（故障链/拓扑），
 * 构建 {@link KnowledgeContext}，并从规程片段中提炼硬性约束（必备传感器 / 禁飞）。
 * <p>基础设施不可用或未命中时返回空上下文，调度降级为纯数据驱动。
 *
 * @author raccoon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPlanningKnowledgeService {

    private final AgentKnowledgeClient agentKnowledgeClient;
    private final SensorRequirementResolver sensorRequirementResolver;

    /**
     * 为任务分配构建混合知识上下文。
     *
     * @param task 标准化巡检任务（非空）
     * @param pool 关联终端池（暂用于检索语境扩展，可空）
     * @return 知识上下文（不会为 null）
     */
    public KnowledgeContext buildKnowledgeContext(DispatchInspectionTask task, List<TerminalState> pool) {
        KnowledgeContext ctx = new KnowledgeContext();
        if (task == null || !agentKnowledgeClient.isEnabled()) {
            return ctx;
        }
        long start = System.currentTimeMillis();
        String query = buildSearchQuery(task);
        List<Long> deviceIds = task.getDeviceIds() == null ? List.of() : task.getDeviceIds();

        CompletableFuture<List<DocumentChunk>> chunkF = CompletableFuture.supplyAsync(
                () -> agentKnowledgeClient.searchRegulations(query, 5));
        CompletableFuture<List<com.raccoon.cloud.drone.dispatch.model.knowledge.DeviceFaultDocRelation>> faultF =
                CompletableFuture.supplyAsync(() -> agentKnowledgeClient.queryFaultRelations(deviceIds));
        CompletableFuture<List<com.raccoon.cloud.drone.dispatch.model.knowledge.TopologyNode>> topoF =
                CompletableFuture.supplyAsync(() -> agentKnowledgeClient.queryTopology(deviceIds));

        try {
            CompletableFuture.allOf(chunkF, faultF, topoF).join();
            ctx.setRegulationChunks(chunkF.get());
            ctx.setFaultRelations(faultF.get());
            ctx.setTopologyNodes(topoF.get());
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            log.warn("[knowledge] 知识上下文构建异常，降级为空: {}", e.getMessage());
            return new KnowledgeContext();
        }

        ctx.setHardConstraints(extractConstraints(ctx));
        log.info("[knowledge] taskId={} query='{}' chunks={} faults={} topo={} constraints={} elapsedMs={}",
                task.getTaskId(), query, ctx.getRegulationChunks().size(), ctx.getFaultRelations().size(),
                ctx.getTopologyNodes().size(), ctx.getHardConstraints().size(),
                System.currentTimeMillis() - start);
        return ctx;
    }

    /** 构造检索 Query：任务类型 + 区域 + 设备 + 传感器语义 + 原始指令。 */
    private String buildSearchQuery(DispatchInspectionTask task) {
        StringBuilder sb = new StringBuilder();
        if (task.getTaskType() != null) {
            sb.append(task.getTaskType().getCode()).append(' ');
        }
        if (StringUtils.hasText(task.getAreaName())) {
            sb.append(task.getAreaName()).append(' ');
        }
        if (task.getDeviceNames() != null && !task.getDeviceNames().isEmpty()) {
            sb.append(String.join(" ", task.getDeviceNames())).append(' ');
        }
        if (task.getRequiredSensors() != null && !task.getRequiredSensors().isEmpty()) {
            sb.append(String.join(" ", task.getRequiredSensors())).append(' ');
        }
        if (StringUtils.hasText(task.getUserInput())) {
            sb.append(task.getUserInput()).append(' ');
        }
        sb.append("巡检规程 作业标准");
        return sb.toString().trim();
    }

    /** 从规程片段中提炼硬性规划约束（必备传感器 / 禁飞）。 */
    private List<PlanningConstraint> extractConstraints(KnowledgeContext ctx) {
        List<PlanningConstraint> constraints = new ArrayList<>();
        for (DocumentChunk c : ctx.getRegulationChunks()) {
            String txt = c.getExcerpt() == null ? "" : c.getExcerpt();
            if (containsAny(txt, "热成像", "红外", "测温")) {
                constraints.add(new PlanningConstraint().setCode("REQUIRE_THERMAL")
                        .setDescription("规程要求热成像/红外巡检").setRequiredSensor("THERMAL_IR")
                        .setSourceChunkId(c.getChunkId()));
            }
            if (containsAny(txt, "激光", "点云", "三维建模")) {
                constraints.add(new PlanningConstraint().setCode("REQUIRE_LIDAR")
                        .setDescription("规程要求激光雷达扫描").setRequiredSensor("LIDAR")
                        .setSourceChunkId(c.getChunkId()));
            }
            if (containsAny(txt, "气体", "可燃气", "甲烷")) {
                constraints.add(new PlanningConstraint().setCode("REQUIRE_GAS")
                        .setDescription("规程要求气体检测").setRequiredSensor("GAS")
                        .setSourceChunkId(c.getChunkId()));
            }
            if (containsAny(txt, "雨天禁", "禁止飞行", "大风禁", "恶劣天气禁")) {
                constraints.add(new PlanningConstraint().setCode("NO_FLY_RAIN")
                        .setDescription("规程约束：恶劣天气禁止无人机巡检")
                        .setSourceChunkId(c.getChunkId()));
            }
        }
        return constraints;
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
