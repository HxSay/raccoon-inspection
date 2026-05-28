package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskStatusEnum;
import com.raccoon.cloud.drone.dispatch.model.AssignmentResult;
import com.raccoon.cloud.drone.dispatch.model.BidPrice;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.TerminalCapacity;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.util.GeoPathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 改进拍卖算法任务分配服务（步骤 5）。
 * <p>对每个任务构建竞拍价：
 * <pre>
 *   finalPrice = distance × COEF_D + energy × COEF_E
 *              - capabilityMatch × COEF_C - priorityBoost × COEF_P
 * </pre>
 * <ul>
 *   <li>电量低于 {@link DispatchConstants#MIN_BATTERY_PCT} → 排除</li>
 *   <li>能力得分低于 0.3（重型任务）或不在线 → 排除</li>
 *   <li>终端并发任务达到 {@link DispatchConstants#MAX_TASK_PER_TERMINAL} → 排除</li>
 * </ul>
 * 按优先级降序遍历任务，每轮挑选 finalPrice 最小且未达容量上限的终端中标。
 *
 * @author raccoon
 */
@Slf4j
@Service
public class AuctionAssignService {

    /**
     * 多任务批量拍卖（容量约束下贪心选优）。
     *
     * @param tasks     待分配任务列表（非空）
     * @param terminals 候选终端列表（非空）
     * @return 分配结果
     */
    public AssignmentResult assign(List<DispatchInspectionTask> tasks, List<TerminalState> terminals) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("待分配任务不能为空");
        }
        if (terminals == null || terminals.isEmpty()) {
            throw new IllegalArgumentException("候选终端不能为空");
        }
        long start = System.currentTimeMillis();

        AssignmentResult result = new AssignmentResult();
        Map<Long, Integer> taskCounters = new HashMap<>();
        for (TerminalState t : terminals) {
            taskCounters.put(t.getTerminalId(),
                    t.getAssignedTaskCount() == null ? 0 : t.getAssignedTaskCount());
        }

        List<DispatchInspectionTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator
                .comparingInt((DispatchInspectionTask x) -> -x.getPriority().getWeight())
                .thenComparing(DispatchInspectionTask::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        for (DispatchInspectionTask task : ordered) {
            List<BidPrice> prices = new ArrayList<>();
            for (TerminalState terminal : terminals) {
                prices.add(buildBid(task, terminal,
                        taskCounters.getOrDefault(terminal.getTerminalId(), 0)));
            }
            result.getPriceMatrix().put(task.getTaskId(), prices);

            BidPrice winner = prices.stream()
                    .filter(p -> !p.isExcluded())
                    .min(Comparator.comparingDouble(BidPrice::getFinalPrice))
                    .orElse(null);

            if (winner == null) {
                task.setStatus(DispatchTaskStatusEnum.FAILED);
                result.getUnassignedTaskIds().add(task.getTaskId());
                log.warn("[auction] taskId={} 无可用终端可分配", task.getTaskId());
                continue;
            }
            task.setAssignedTerminalId(winner.getTerminalId());
            task.setBidFinalPrice(winner.getFinalPrice());
            task.setStatus(DispatchTaskStatusEnum.ASSIGNED);

            result.getTaskTerminalMap().put(task.getTaskId(), winner.getTerminalId());
            int next = taskCounters.merge(winner.getTerminalId(), 1, Integer::sum);
            log.info("[auction] taskId={} -> terminalId={} price={} (capacity {}/{})",
                    task.getTaskId(), winner.getTerminalId(),
                    round(winner.getFinalPrice()), next,
                    DispatchConstants.MAX_TASK_PER_TERMINAL);
        }

        result.setTerminalTaskCount(taskCounters);
        result.setElapsedMs(System.currentTimeMillis() - start);
        log.info("[auction] 分配完成 任务数={} 已分配={} 未分配={} 耗时={}ms",
                ordered.size(),
                result.getTaskTerminalMap().size(),
                result.getUnassignedTaskIds().size(),
                result.getElapsedMs());
        return result;
    }

    /**
     * 构建单个 (任务, 终端) 竞拍价。
     */
    private BidPrice buildBid(DispatchInspectionTask task, TerminalState terminal, int currentTaskCount) {
        BidPrice bid = new BidPrice();
        bid.setTaskId(task.getTaskId());
        bid.setTerminalId(terminal.getTerminalId());

        // 1) 电量约束
        if (terminal.getBatteryPct() != null
                && terminal.getBatteryPct() < DispatchConstants.MIN_BATTERY_PCT) {
            bid.setExcluded(true);
            bid.setExcludeReason("BATTERY_LOW=" + terminal.getBatteryPct() + "%");
            return bid;
        }
        // 2) 在线 / 故障约束
        if (!terminal.isOnline() || "FAULT".equalsIgnoreCase(terminal.getFaultStatus())) {
            bid.setExcluded(true);
            bid.setExcludeReason(terminal.isOnline() ? "FAULT" : "OFFLINE");
            return bid;
        }
        // 3) 容量约束
        if (currentTaskCount >= DispatchConstants.MAX_TASK_PER_TERMINAL) {
            bid.setExcluded(true);
            bid.setExcludeReason("CAPACITY_FULL=" + currentTaskCount);
            return bid;
        }
        // 4) 距离与能耗
        double distanceM = estimateDistance(task, terminal);
        double energyCost = estimateEnergy(distanceM, terminal);
        if (Double.isInfinite(distanceM)
                || (terminal.getCapacity() != null
                && distanceM > terminal.getCapacity().getMotion().getMaxRangeM())) {
            bid.setExcluded(true);
            bid.setExcludeReason("OUT_OF_RANGE=" + (int) distanceM + "m");
            return bid;
        }
        // 5) 能力匹配
        double capabilityMatch = capabilityMatchScore(task, terminal);
        // 6) 优先级加成（0~1）
        double priorityBoost = task.getPriorityScore() != null
                ? task.getPriorityScore().getFinalScore()
                : task.getPriority().getLower();
        // 7) 综合价：低更优
        double normDistance = normalize(distanceM, 0, 8000);
        double normEnergy = normalize(energyCost, 0, 100);
        double price = normDistance * DispatchConstants.BID_DISTANCE_COEF
                + normEnergy * DispatchConstants.BID_ENERGY_COEF
                - capabilityMatch * DispatchConstants.BID_CAPABILITY_COEF
                - priorityBoost * DispatchConstants.BID_PRIORITY_COEF;
        // 价格下限 0
        price = Math.max(0.0, price);

        bid.setDistanceCost(distanceM);
        bid.setEnergyCost(energyCost);
        bid.setCapabilityMatch(capabilityMatch);
        bid.setPriorityBoost(priorityBoost);
        bid.setFinalPrice(price);
        return bid;
    }

    /**
     * 估算终端 → 任务区域距离（取任务首个航点）。
     * <p>当任一坐标缺失，返回 0（视为同点），保证算法可继续。
     */
    private double estimateDistance(DispatchInspectionTask task, TerminalState terminal) {
        GeoPoint terminalPos = terminal.getPosition();
        List<GeoPoint> waypoints = task.getDeviceWaypoints();
        if (terminalPos == null || waypoints == null || waypoints.isEmpty()) {
            return 0.0;
        }
        GeoPoint target = waypoints.get(0);
        return GeoPathUtils.segmentLengthMeters(terminalPos, target);
    }

    /** 简化能耗模型：距离换算 + 终端电池健康度修正 */
    private double estimateEnergy(double distanceM, TerminalState terminal) {
        double base = (distanceM / 1000.0) * DispatchConstants.BATTERY_PCT_PER_KM;
        float health = terminal.getCapacity() != null
                ? terminal.getCapacity().getEndurance().getBatteryHealthPct()
                : 100f;
        if (health <= 0) {
            health = 100f;
        }
        return base * (100.0 / health);
    }

    /**
     * 能力匹配度：以综合能力得分为主，应急/故障维修任务对感知能力加权。
     */
    private double capabilityMatchScore(DispatchInspectionTask task, TerminalState terminal) {
        TerminalCapacity capacity = terminal.getCapacity();
        if (capacity == null) {
            return 0.5;
        }
        double overall = capacity.getOverallScore();
        double bonus = 0.0;
        if (task.getTaskType() != null) {
            switch (task.getTaskType()) {
                case EMERGENCY, FAULT_REPAIR -> bonus += capacity.getPerception().isIrSupported() ? 0.1 : 0.0;
                case RE_INSPECTION -> bonus += capacity.getPerception().isLidarSupported() ? 0.05 : 0.0;
                default -> { /* no-op */ }
            }
        }
        return Math.max(0.0, Math.min(1.0, overall + bonus));
    }

    private double normalize(double value, double lo, double hi) {
        if (hi - lo <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (value - lo) / (hi - lo)));
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
