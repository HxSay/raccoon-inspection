package com.raccoon.cloud.drone.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InspectionRobotTelemetryReport {

    @NotNull
    private Long uavId;

    /** POSITION / BATTERY / LOAD / RUNTIME — 可组合上报 */
    private String channel;

    private Double longitude;
    private Double latitude;
    private Double height;

    private Float batteryPct;
    private Integer enduranceMin;

    private Integer assignedTaskCount;
    private Float cpuPct;
    private Float memoryPct;

    private Boolean online;
    private String flightStatus;
    private String faultStatus;
    private String faultMessage;
}
