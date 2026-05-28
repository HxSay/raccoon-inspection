package com.raccoon.cloud.drone.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("uav_robot_runtime_status")
public class UavRobotRuntimeStatus {

    @TableId
    private Long uavId;

    private Double longitude;
    private Double latitude;
    private Double height;

    private Float batteryPct;
    private Integer enduranceMin;

    private Integer assignedTaskCount;
    private Float cpuPct;
    private Float memoryPct;

    private Integer onlineFlag;
    private String flightStatus;
    private String faultStatus;
    private String faultMessage;

    private LocalDateTime positionAt;
    private LocalDateTime batteryAt;
    private LocalDateTime loadAt;
    private LocalDateTime runtimeAt;
    private LocalDateTime updateTime;
}
