package com.raccoon.cloud.agent.ai.milvus.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Milvus 测试：入库结果。
 */
@Data
@Builder
public class MilvusTestInsertResultVO {

    private String docId;
    private String message;
    private Map<String, Object> metadata;
}
