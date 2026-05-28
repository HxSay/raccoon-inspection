package com.raccoon.cloud.drone.dispatch.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 拍卖算法竞拍价：表示某终端对某任务的报价。
 * <p>价格越低代表性价比越高（成本最小化），但通过 priorityBoost 反向加成高优先级任务。
 * {@code excluded=true} 表示该终端因电量/能力/容量约束被强制排除，不参与该任务的最终选优。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class BidPrice {

    /** 终端 ID */
    private Long terminalId;

    /** 任务 ID */
    private String taskId;

    /** 距离成本（米） */
    private double distanceCost;

    /** 能耗成本（电量 %） */
    private double energyCost;

    /** 能力匹配度 0~1（值越大越匹配） */
    private double capabilityMatch;

    /** 优先级加成 0~1，根据任务 priorityScore 增大对高优任务的偏好 */
    private double priorityBoost;

    /** 综合竞拍价（值越小越优） */
    private double finalPrice;

    /** 是否被排除（电量不足 / 能力不达标 / 任务容量满） */
    private boolean excluded;

    /** 排除原因（excluded=true 时填充） */
    private String excludeReason;
}
