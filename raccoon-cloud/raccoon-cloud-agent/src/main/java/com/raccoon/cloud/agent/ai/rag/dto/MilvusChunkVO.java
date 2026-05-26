package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Milvus 一条 chunk（向量主键 + 文本 + metadata）。
 */
@Data
@Builder
public class MilvusChunkVO {

    private String docPk;

    private String docId;

    private String fileName;

    private Integer chunkIndex;

    private String content;

    private Map<String, Object> metadata;
}
