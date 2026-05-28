package com.raccoon.cloud.drone.dto;

import lombok.Data;

@Data
public class InspectionRobotVO {

    private Long id;
    private String uavName;
    private String uavCode;
    private String robotType;
    private String robotTypeLabel;
    private Long mapId;
    private String mapName;
    private String sceneType;
    private String markerLabel;
    private String markerColor;
    private Double sceneX;
    private Double sceneY;
    private Double sceneZ;
    private Integer status;
    private String remark;
}
