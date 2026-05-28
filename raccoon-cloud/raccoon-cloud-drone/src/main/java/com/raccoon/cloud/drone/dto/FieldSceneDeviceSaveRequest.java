package com.raccoon.cloud.drone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FieldSceneDeviceSaveRequest {

    private Long id;

    @NotNull(message = "虚拟场景不能为空")
    private Long mapId;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    private Double longitude;
    private Double latitude;
    private Double height;
    private Double sceneX;
    private Double sceneY;
    private Double sceneZ;
    private String sceneType;
    private String sceneObjectId;
    private String locationDesc;
    private String syncSource = "MANUAL";
    private Integer status = 1;
    private String remark;
}
