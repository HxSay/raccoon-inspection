package com.raccoon.cloud.iotdata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UavInspectionUploadResult {
    private Long sessionId;
    private int sampleCount;
    private boolean neo4jSynced;
    private String neo4jMessage;
    private boolean milvusSynced;
    private String milvusMessage;
    private String ragDocId;
    private Integer ragChunkCount;
}
