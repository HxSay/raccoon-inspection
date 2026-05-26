package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 文档列表项（来自 Neo4j Document 节点）。
 * 一个文档可以关联多个设备。
 */
@Data
@Builder
public class RagDocumentVO {

    private String docId;

    private String fileName;

    private String docType;

    /** 该文档关联的全部设备（多对多） */
    private List<RagDeviceVO> devices;

    private Long uploadTime;

    private Integer chunkCount;

    private String minioObjectKey;
}
