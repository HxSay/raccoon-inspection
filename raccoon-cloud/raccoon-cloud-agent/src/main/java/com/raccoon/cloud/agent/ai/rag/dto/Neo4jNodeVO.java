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

    /** Document 节点：关联的设备（Device-[HAS_DOCUMENT]->Document） */
    private List<Map<String, Object>> relatedDevices;

    /** Device 节点：关联的文档 */
    private List<Map<String, Object>> relatedDocuments;
}
