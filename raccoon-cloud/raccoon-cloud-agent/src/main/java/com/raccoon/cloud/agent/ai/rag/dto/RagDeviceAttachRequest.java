package com.raccoon.cloud.agent.ai.rag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 给文档批量关联设备的请求：携带 CMMS 设备主数据字段，
 * 后端 MERGE Device 时一并写入 Neo4j，实现"主数据自动同步"。
 */
@Data
public class RagDeviceAttachRequest {

    /** 完整的设备信息列表（来自设备管理） */
    @Valid
    @NotEmpty(message = "devices 不能为空")
    private List<DeviceInput> devices;

    @Data
    public static class DeviceInput {

        /** 设备业务主键：对应 CMMS deviceCode，或用户自由输入的新 ID */
        @NotBlank(message = "deviceId 不能为空")
        private String deviceId;

        /** 设备名称（来自 deviceName） */
        private String name;

        /** 设备类型 / 型号（来自 model 或分类名） */
        private String type;

        /** 设备所在站点 / 位置（来自 location） */
        private String station;
    }
}
