package com.raccoon.cloud.drone.fault.enums;

import lombok.Getter;

@Getter
public enum FaultHandleStatus {

    SUCCESS("SUCCESS"),
    PARTIAL("PARTIAL"),
    FAILED("FAILED"),
    MERGED("MERGED");

    private final String code;

    FaultHandleStatus(String code) {
        this.code = code;
    }
}
