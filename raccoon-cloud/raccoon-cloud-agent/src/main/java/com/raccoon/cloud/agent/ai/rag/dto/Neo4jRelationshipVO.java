package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Neo4j 关系视图，附带两端节点的关键字段。
 */
@Data
@Builder
public class Neo4jRelationshipVO {

    /** Neo4j 关系内部 ID，用于删除 */
    private Long relInternalId;

    private String type;

    private Map<String, Object> properties;

    private String startLabel;

    private Map<String, Object> startNode;

    private String endLabel;

    private Map<String, Object> endNode;
}
