package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 设备列表项：取自 Neo4j Device 节点。
 */
@Data
@Builder
public class RagDeviceVO {

    private String deviceId;

    private String name;

    private String type;

    private String station;
}
