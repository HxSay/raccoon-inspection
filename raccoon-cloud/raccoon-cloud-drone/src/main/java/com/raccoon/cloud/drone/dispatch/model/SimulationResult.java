package com.raccoon.cloud.drone.dispatch.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 数字孪生预演结果。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class SimulationResult {

    /** 是否通过预演 */
    private boolean passed;

    /** 检出冲突列表 */
    private List<ConflictInfo> conflicts = new ArrayList<>();

    /** PPO 模型奖励值，越大越优 */
    private double ppoReward;

    /** 预演耗时 ms */
    private long elapsedMs;

    /** 是否根据预演调整了分配（true 表示触发了 reroute/swap） */
    private boolean adjusted;

    /** 调整建议描述 */
    private String adjustmentNote;
}
