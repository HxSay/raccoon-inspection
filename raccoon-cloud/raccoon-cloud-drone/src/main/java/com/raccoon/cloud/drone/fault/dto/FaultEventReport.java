package com.raccoon.cloud.drone.fault.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 边缘 / 终端 / 人工统一异常上报。
 */
@Data
public class FaultEventReport {

    private Long deviceId;
    private Long terminalId;
    private Long mapId;

    @NotBlank(message = "故障类型不能为空")
    private String faultType;

    private Double confidence;
    private String description;
    private String dataUrl;
    private Map<String, Object> extData = new HashMap<>();

    /**
     * 仿真/演示场景为 true 时跳过「5 分钟内同设备同类型合并」，便于重复触发分级与扩范围复巡。
     */
    private Boolean skipDedupe;
}
