package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dispatch.enums.DispatchPriorityEnum;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.TaskPriorityScore;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DeviceFaultDocRelation;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DocumentChunk;
import com.raccoon.cloud.drone.dispatch.model.knowledge.KnowledgeContext;
import com.raccoon.cloud.drone.dispatch.model.knowledge.PlanningConstraint;
import com.raccoon.cloud.drone.dispatch.model.knowledge.TopologyNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 任务优先级评估服务（步骤 4）。
 * <p>五维加权算法：
 * <ul>
 *   <li>故障历史 {@code WEIGHT_FAULT_HISTORY}=30%</li>
 *   <li>系统负载 {@code WEIGHT_LOAD}=25%</li>
 *   <li>关键等级 {@code WEIGHT_CRITICAL}=20%</li>
 *   <li>风险等级 {@code WEIGHT_RISK}=15%</li>
 *   <li>巡检间隔 {@code WEIGHT_INTERVAL}=10%</li>
 * </ul>
 * 所有维度归一化至 [0,1]，加权得分映射至 {@link DispatchPriorityEnum}。
 *
 * @author raccoon
 */
@Slf4j
@Service
public class TaskPriorityService {

    /**
     * 评估任务优先级。
     *
     * @param task         任务（非空）
     * @param terminalPool 当前场景终端池，用于负载维度，可空
     * @return 评分明细（同时写回 {@code task.priorityScore} 与 {@code task.priority}）
     */
    public TaskPriorityScore evaluate(DispatchInspectionTask task, List<TerminalState> terminalPool) {
        return evaluate(task, terminalPool, task == null ? null : task.getKnowledgeContext());
    }

    /**
     * 知识增强的优先级评估。
     * <p>在五维基础分之上，命中知识时按 {@code base*0.75 + 规程*0.15 + 拓扑*0.05 + 故障图*0.05} 加权；
     * 无知识命中则退化为纯五维分（保持原行为）。同时应用规程硬约束（追加必备传感器 / 阻断）。
     *
     * @param task         任务（非空）
     * @param terminalPool 终端池，用于负载维度，可空
     * @param knowledge    混合 RAG 知识上下文，可空
     * @return 评分明细
     */
    public TaskPriorityScore evaluate(DispatchInspectionTask task, List<TerminalState> terminalPool,
                                      KnowledgeContext knowledge) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        TaskPriorityScore score = new TaskPriorityScore();
        score.setFaultHistoryScore(normalize(task.getFaultHistoryRate()));
        score.setCriticalLevelScore(normalize(task.getCriticalLevel()));
        score.setRiskScore(normalize(task.getRiskLevel()));
        score.setIntervalScore(intervalScore(task.getInspectionIntervalHours()));
        score.setLoadScore(loadScore(terminalPool));

        double baseScore =
                score.getFaultHistoryScore() * DispatchConstants.WEIGHT_FAULT_HISTORY
                        + score.getLoadScore() * DispatchConstants.WEIGHT_LOAD
                        + score.getCriticalLevelScore() * DispatchConstants.WEIGHT_CRITICAL
                        + score.getRiskScore() * DispatchConstants.WEIGHT_RISK
                        + score.getIntervalScore() * DispatchConstants.WEIGHT_INTERVAL;
        score.setBaseScore(clamp01(baseScore));

        // —— 知识增强维度 ——
        score.setRegulationScore(scoreRegulationUrgency(knowledge));
        score.setTopologyScore(scoreTopologyImpact(knowledge));
        score.setFaultGraphScore(scoreFaultGraph(knowledge));

        double finalScore;
        if (knowledge != null && knowledge.hasSignal()) {
            score.setKnowledgeEnhanced(true);
            finalScore = baseScore * DispatchConstants.WEIGHT_KNOWLEDGE_BASE
                    + score.getRegulationScore() * DispatchConstants.WEIGHT_REGULATION
                    + score.getTopologyScore() * DispatchConstants.WEIGHT_TOPOLOGY
                    + score.getFaultGraphScore() * DispatchConstants.WEIGHT_FAULT_GRAPH;
        } else {
            finalScore = baseScore;
        }

        // 规程硬约束（追加必备传感器 / 雨天禁飞阻断）
        applyHardConstraints(task, knowledge);

        // 应急任务直接抬升到 CRITICAL（保留评分明细作可追溯依据）
        if (task.getTaskType() != null) {
            finalScore = Math.max(finalScore, task.getTaskType().getIntrinsicWeight());
        }
        finalScore = clamp01(finalScore);
        score.setFinalScore(finalScore);
        score.setPriorityLevel(DispatchPriorityEnum.fromScore(finalScore));

        task.setPriorityScore(score);
        if (task.getPriority() == null
                || task.getPriority().getWeight() < score.getPriorityLevel().getWeight()) {
            task.setPriority(score.getPriorityLevel());
        }
        log.info("[priority] taskId={} final={} base={} reg={} topo={} faultG={} knowledge={} -> {}",
                task.getTaskId(),
                round(score.getFinalScore()), round(score.getBaseScore()),
                round(score.getRegulationScore()), round(score.getTopologyScore()),
                round(score.getFaultGraphScore()), score.isKnowledgeEnhanced(),
                score.getPriorityLevel().getCode());
        return score;
    }

    /** 规程紧迫度：检索片段含「立即复巡/加密/24小时/紧急/异常」等关键词得分更高。 */
    private double scoreRegulationUrgency(KnowledgeContext knowledge) {
        if (knowledge == null || knowledge.getRegulationChunks().isEmpty()) {
            return 0.0;
        }
        String[] urgentKeys = {"立即", "加密", "24小时", "紧急", "异常", "停运", "复巡", "禁止"};
        double max = 0.4; // 命中规程即给基础紧迫度
        for (DocumentChunk c : knowledge.getRegulationChunks()) {
            String txt = c.getExcerpt() == null ? "" : c.getExcerpt();
            for (String k : urgentKeys) {
                if (txt.contains(k)) {
                    max = Math.max(max, 0.9);
                }
            }
        }
        return clamp01(max);
    }

    /** 拓扑影响：下游关键设备越多、关键等级越高，分越高。 */
    private double scoreTopologyImpact(KnowledgeContext knowledge) {
        if (knowledge == null || knowledge.getTopologyNodes().isEmpty()) {
            return 0.0;
        }
        List<TopologyNode> nodes = knowledge.getTopologyNodes();
        double countScore = clamp01(nodes.size() / 5.0);
        double critical = nodes.stream()
                .map(TopologyNode::getCriticalLevel)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max().orElse(0.0);
        return clamp01(countScore * 0.5 + clamp01(critical) * 0.5);
    }

    /** 故障关联：未闭环 / 高 severity 故障越多分越高。 */
    private double scoreFaultGraph(KnowledgeContext knowledge) {
        if (knowledge == null || knowledge.getFaultRelations().isEmpty()) {
            return 0.0;
        }
        double maxSeverity = knowledge.getFaultRelations().stream()
                .map(DeviceFaultDocRelation::getSeverity)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max().orElse(0.0);
        double countScore = clamp01(knowledge.getFaultRelations().size() / 5.0);
        return clamp01(clamp01(maxSeverity / 5.0) * 0.7 + countScore * 0.3);
    }

    /** 应用规程硬约束：追加必备传感器；雨天禁飞则阻断任务。 */
    private void applyHardConstraints(DispatchInspectionTask task, KnowledgeContext knowledge) {
        if (knowledge == null || knowledge.getHardConstraints().isEmpty()) {
            return;
        }
        boolean rainy = Boolean.parseBoolean(String.valueOf(task.getExtension().getOrDefault("rainy", "false")));
        for (PlanningConstraint c : knowledge.getHardConstraints()) {
            if (c.getCode() == null) {
                continue;
            }
            if (StringUtils.hasText(c.getRequiredSensor())) {
                task.getRequiredSensors().add(c.getRequiredSensor());
            }
            if ("NO_FLY_RAIN".equals(c.getCode()) && rainy) {
                task.setBlocked(true);
                task.setBlockReason("规程约束：" + (StringUtils.hasText(c.getDescription())
                        ? c.getDescription() : "雨天禁止无人机巡检"));
            }
        }
    }

    /**
     * 巡检间隔评分：默认周期 24h，间隔越久得分越高，在 48h 处达到 1。
     */
    private double intervalScore(double intervalHours) {
        if (intervalHours <= 0) {
            return 0.0;
        }
        return clamp01(intervalHours / 48.0);
    }

    /**
     * 负载评分：终端池平均负载越高、可用机器越少，得分越高（任务越紧迫）。
     */
    private double loadScore(List<TerminalState> pool) {
        if (pool == null || pool.isEmpty()) {
            return 0.5; // 无可用终端默认中等紧迫
        }
        double avgCpu = pool.stream()
                .map(TerminalState::getCpuPct)
                .filter(v -> v != null)
                .mapToDouble(Float::doubleValue)
                .average().orElse(0.0);
        double avgTaskCount = pool.stream()
                .map(TerminalState::getAssignedTaskCount)
                .filter(v -> v != null)
                .mapToDouble(Integer::doubleValue)
                .average().orElse(0.0);
        long busyCount = pool.stream().filter(s -> !s.canAcceptTask(
                DispatchConstants.MIN_BATTERY_PCT, DispatchConstants.MAX_TASK_PER_TERMINAL)).count();
        double busyRatio = (double) busyCount / pool.size();
        double cpuScore = normalize(avgCpu / 100.0);
        double taskScore = normalize(avgTaskCount / DispatchConstants.MAX_TASK_PER_TERMINAL);
        // CPU 与并发任务等权 + 不可用占比加权
        return clamp01(cpuScore * 0.4 + taskScore * 0.4 + busyRatio * 0.2);
    }

    private double normalize(double v) {
        return clamp01(v);
    }

    private double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
