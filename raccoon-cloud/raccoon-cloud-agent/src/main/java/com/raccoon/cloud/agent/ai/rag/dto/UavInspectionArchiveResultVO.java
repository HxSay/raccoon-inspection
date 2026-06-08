package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UavInspectionArchiveResultVO {
    private String docId;
    private String fileName;
    private Integer chunkCount;
    private Boolean milvusSynced;
    private Boolean neo4jSynced;
    private String message;
}
