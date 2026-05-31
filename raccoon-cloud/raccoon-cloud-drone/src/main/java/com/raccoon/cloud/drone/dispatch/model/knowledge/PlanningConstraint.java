package com.raccoon.cloud.drone.dispatch.model.knowledge;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 从巡检规程 / 故障手册中解析出的硬性规划约束。
 * <p>由混合 RAG 知识上下文（{@link KnowledgeContext}）提炼，作为优先级评分与拍卖分配的强约束输入：
 * 例如「主变温度异常须热成像复巡」「雨天禁止无人机巡检」。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class PlanningConstraint {

    /** 约束编码，如 REQUIRE_THERMAL / NO_FLY_RAIN / REQUIRE_LIDAR */
    private String code;

    /** 约束描述（用于可解释展示） */
    private String description;

    /** 该约束要求的传感器类型（可空，如 THERMAL_IR / LIDAR / GAS） */
    private String requiredSensor;

    /** 引用的知识片段 ID（Milvus chunkId），用于审计追溯 */
    private String sourceChunkId;
}
