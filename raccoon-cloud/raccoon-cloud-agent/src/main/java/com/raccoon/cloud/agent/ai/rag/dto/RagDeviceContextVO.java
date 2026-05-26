package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 设备关联上下文：故障 / 上下游设备。
 */
@Data
@Builder
public class RagDeviceContextVO {

    private RagDeviceVO device;

    private List<FaultInfo> faults;

    private List<String> upstreamDevices;

    private List<String> downstreamDevices;

    @Data
    @Builder
    public static class FaultInfo {
        private String faultCode;
        private String name;
        private String description;
        private Integer level;
    }
}
