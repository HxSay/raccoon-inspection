package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dispatch.enums.DispatchPriorityEnum;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.TaskPriorityScore;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        if (task == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        TaskPriorityScore score = new TaskPriorityScore();
        score.setFaultHistoryScore(normalize(task.getFaultHistoryRate()));
        score.setCriticalLevelScore(normalize(task.getCriticalLevel()));
        score.setRiskScore(normalize(task.getRiskLevel()));
        score.setIntervalScore(intervalScore(task.getInspectionIntervalHours()));
        score.setLoadScore(loadScore(terminalPool));

        double finalScore =
                score.getFaultHistoryScore() * DispatchConstants.WEIGHT_FAULT_HISTORY
                        + score.getLoadScore() * DispatchConstants.WEIGHT_LOAD
                        + score.getCriticalLevelScore() * DispatchConstants.WEIGHT_CRITICAL
                        + score.getRiskScore() * DispatchConstants.WEIGHT_RISK
                        + score.getIntervalScore() * DispatchConstants.WEIGHT_INTERVAL;

        // 应急任务直接抬升到 CRITICAL（保留五维评分作可追溯依据）
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
        log.info("[priority] taskId={} score={} fault={} load={} critical={} risk={} interval={} -> {}",
                task.getTaskId(),
                round(score.getFinalScore()), round(score.getFaultHistoryScore()),
                round(score.getLoadScore()), round(score.getCriticalLevelScore()),
                round(score.getRiskScore()), round(score.getIntervalScore()),
                score.getPriorityLevel().getCode());
        return score;
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
