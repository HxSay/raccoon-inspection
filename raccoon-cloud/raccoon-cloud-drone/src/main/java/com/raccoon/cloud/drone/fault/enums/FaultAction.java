package com.raccoon.cloud.drone.fault.enums;

import lombok.Getter;

/**
 * 分级处置动作。
 */
@Getter
public enum FaultAction {

    SCHEDULED_REINSPECT("SCHEDULED_REINSPECT", "计划复巡"),
    IMMEDIATE_REINSPECT("IMMEDIATE_REINSPECT", "立即复巡"),
    EMERGENCY_SWEEP("EMERGENCY_SWEEP", "应急全面核查");

    private final String code;
    private final String label;

    FaultAction(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
