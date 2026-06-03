package com.raccoon.cloud.drone.fault.dto;

import lombok.Data;

@Data
public class EnvironmentAlertReport {

    private Long mapId;
    private String alertType;
    private String description;
    private Double severity;
}
