package com.raccoon.cloud.drone.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("uav_route_plan")
public class UavRoutePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long mapId;
    private Long uavId;
    private String startPoint;
    private String endPoint;
    private Double totalDistance;
    private Integer estimatedTime;
    private Float estimatedBattery;
    private String algorithm;
    private String pathPoints;
    private String photoPoints;
    private String visitOrder;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
