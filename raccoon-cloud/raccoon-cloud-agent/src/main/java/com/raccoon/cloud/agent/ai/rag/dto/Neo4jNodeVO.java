package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Neo4j 节点视图。
 */
@Data
@Builder
public class Neo4jNodeVO {

    private Long internalId;

    private List<String> labels;

    private Map<String, Object> properties;
}
