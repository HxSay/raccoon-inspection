package com.raccoon.cloud.drone.enums;

import lombok.Getter;

@Getter
public enum InspectionRobotTypeEnum {

    UAV("UAV", "无人机"),
    ROBOT_DOG("ROBOT_DOG", "机器狗"),
    GROUND_ROBOT("GROUND_ROBOT", "地面巡检机器人"),
    WHEELED("WHEELED", "轮式巡检机器人");

    private final String code;
    private final String label;

    InspectionRobotTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(String code) {
        if (code == null) {
            return "";
        }
        for (InspectionRobotTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e.label;
            }
        }
        return code;
    }
}
