package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 检索引用：向量片段或图谱信息。
 */
@Data
@Builder
public class RagReferenceVO {

    /** 引用来源：vector / graph */
    private String source;

    private String docId;

    private String fileName;

    private Integer chunkIndex;

    private Double score;

    private String content;

    private String minioUrl;

    private String deviceId;
}
