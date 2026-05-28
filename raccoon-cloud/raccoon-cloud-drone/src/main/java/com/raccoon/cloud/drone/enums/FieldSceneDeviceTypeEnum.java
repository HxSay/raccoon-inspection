package com.raccoon.cloud.drone.enums;

import lombok.Getter;

@Getter
public enum FieldSceneDeviceTypeEnum {

    TOWER("TOWER", "杆塔"),
    TRANSFORMER("TRANSFORMER", "变压器"),
    BREAKER("BREAKER", "断路器"),
    VALVE("VALVE", "阀门"),
    ISOLATOR("ISOLATOR", "隔离开关"),
    STATION("STATION", "站房"),
    CUSTOM("CUSTOM", "自定义设备");

    private final String code;
    private final String label;

    FieldSceneDeviceTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(String code) {
        if (code == null) {
            return "";
        }
        for (FieldSceneDeviceTypeEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e.label;
            }
        }
        return code;
    }
}
