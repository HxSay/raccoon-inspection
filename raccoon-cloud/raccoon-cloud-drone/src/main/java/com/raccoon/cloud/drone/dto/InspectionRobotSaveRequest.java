package com.raccoon.cloud.drone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InspectionRobotSaveRequest {

    private Long id;

    @NotBlank(message = "机器人名称不能为空")
    private String uavName;

    private String uavCode;

    @NotBlank(message = "机器人类型不能为空")
    private String robotType;

    @NotNull(message = "虚拟场景不能为空")
    private Long mapId;

    @NotBlank(message = "场景标记不能为空")
    private String markerLabel;

    private String markerColor = "#409EFF";

    private Double sceneX;
    private Double sceneY;
    private Double sceneZ;

    private Integer status = 1;
    private String remark;
}
