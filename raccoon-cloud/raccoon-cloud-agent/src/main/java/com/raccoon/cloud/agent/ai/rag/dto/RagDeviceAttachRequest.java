package com.raccoon.cloud.agent.ai.rag.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 给文档批量关联设备的请求（仅追加，不影响已有关联）。
 */
@Data
public class RagDeviceAttachRequest {

    @NotEmpty(message = "deviceIds 不能为空")
    private List<String> deviceIds;
}
