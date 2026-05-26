package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 文档入库结果。
 */
@Data
@Builder
public class IngestResultVO {

    private String docId;

    private String fileName;

    private Long uploadTime;

    private String deviceId;

    private String docType;

    private Integer chunkCount;

    private String minioObjectKey;

    private String minioUrl;
}
