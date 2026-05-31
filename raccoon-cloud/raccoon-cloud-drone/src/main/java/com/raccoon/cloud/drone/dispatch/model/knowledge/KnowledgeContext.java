package com.raccoon.cloud.drone.dispatch.model.knowledge;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合 RAG 知识上下文（步骤 2）。
 * <p>任务分配前由 {@code TaskPlanningKnowledgeService} 并行执行 Milvus 语义检索与 Neo4j 图查询构建，
 * 为优先级评分与拍卖算法提供规程约束、故障关联与拓扑影响等知识输入。
 * <p>当知识基础设施不可用或无命中时，各列表为空，调度流程降级为纯数据驱动（不阻断）。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class KnowledgeContext {

    /** Milvus 检索到的规程 / 手册片段 */
    private List<DocumentChunk> regulationChunks = new ArrayList<>();

    /** Neo4j 查询到的设备-故障-文档关联 */
    private List<DeviceFaultDocRelation> faultRelations = new ArrayList<>();

    /** Neo4j 查询到的设备拓扑（下游 1~2 层） */
    private List<TopologyNode> topologyNodes = new ArrayList<>();

    /** 从知识中解析出的硬性约束（如：主变巡检必须热成像、雨天禁飞） */
    private List<PlanningConstraint> hardConstraints = new ArrayList<>();

    /** 知识检索是否真正命中（用于决定是否启用知识增强权重） */
    public boolean hasSignal() {
        return !regulationChunks.isEmpty() || !faultRelations.isEmpty() || !topologyNodes.isEmpty();
    }

    /** 是否为空上下文（基础设施降级时返回的空对象） */
    public static KnowledgeContext empty() {
        return new KnowledgeContext();
    }
}
