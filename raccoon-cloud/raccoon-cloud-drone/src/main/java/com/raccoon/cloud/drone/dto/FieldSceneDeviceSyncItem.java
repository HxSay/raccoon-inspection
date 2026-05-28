package com.raccoon.cloud.drone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FieldSceneDeviceSyncItem {

    @NotBlank
    private String sceneObjectId;

    @NotBlank
    private String deviceName;

    private String deviceType = "CUSTOM";

    private Double longitude;
    private Double latitude;
    private Double height;

    private Double sceneX;
    private Double sceneY;
    private Double sceneZ;

    private String locationDesc;
    private String remark;
}
