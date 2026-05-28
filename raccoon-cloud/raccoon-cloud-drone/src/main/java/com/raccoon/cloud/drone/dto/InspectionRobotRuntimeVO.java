package com.raccoon.cloud.drone.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检机器人实时状态（供中央 Agent 决策）。
 */
@Data
public class InspectionRobotRuntimeVO {

    /** 位置信息 10Hz */
    private Double longitude;
    private Double latitude;
    private Double height;
    private LocalDateTime positionAt;

    /** 电量状态 1Hz */
    private Float batteryPct;
    private Integer enduranceMin;
    private LocalDateTime batteryAt;

    /** 负载状态 1Hz */
    private Integer assignedTaskCount;
    private Float cpuPct;
    private Float memoryPct;
    private LocalDateTime loadAt;

    /** 运行状态 10Hz */
    private Boolean online;
    private String flightStatus;
    private String flightStatusLabel;
    private String faultStatus;
    private String faultMessage;
    private LocalDateTime runtimeAt;

    /** 作业范围 静态 */
    private String workRangeDesc;
}
