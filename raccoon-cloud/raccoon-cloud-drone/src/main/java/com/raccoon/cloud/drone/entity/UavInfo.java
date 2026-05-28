package com.raccoon.cloud.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("uav_info")
public class UavInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uavName;
    private String uavCode;

    /** UAV / ROBOT_DOG / GROUND_ROBOT / WHEELED */
    private String robotType;

    /** 仿真场景内识别标记（如 UAV-01） */
    private String markerLabel;

    private String markerColor;

    private Double sceneX;
    private Double sceneY;
    private Double sceneZ;

    private Long mapId;
    private Integer status;
    private String remark;
}
