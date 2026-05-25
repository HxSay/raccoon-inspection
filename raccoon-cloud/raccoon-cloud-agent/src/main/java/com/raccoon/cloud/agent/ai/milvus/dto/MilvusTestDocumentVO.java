package com.raccoon.cloud.agent.ai.milvus.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Milvus 测试：单条文档视图（检索结果 / 按 ID 查询）。
 */
@Data
@Builder
public class MilvusTestDocumentVO {

    private String docId;
    private String content;
    private Map<String, Object> metadata;
    /** 相似度得分，越高越相似；按 ID 查询时可能为空 */
    private Double score;
}
