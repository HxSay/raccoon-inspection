package com.raccoon.cloud.drone.dto;

import lombok.Data;

@Data
public class FieldSceneDeviceVO {

    private Long id;
    private Long mapId;
    private String mapName;
    private String deviceName;
    private String deviceType;
    private String deviceTypeLabel;
    private Double longitude;
    private Double latitude;
    private Double height;
    private Double sceneX;
    private Double sceneY;
    private Double sceneZ;
    private String sceneType;
    private String sceneObjectId;
    private String locationDesc;
    private String syncSource;
    private Integer status;
    private String remark;
}
