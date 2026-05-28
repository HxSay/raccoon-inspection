package com.raccoon.cloud.drone.enums;

import lombok.Getter;

@Getter
public enum RobotFlightStatusEnum {

    STANDBY("STANDBY", "待机"),
    FLYING("FLYING", "飞行中"),
    RTH("RTH", "返航"),
    LANDING("LANDING", "降落"),
    OFFLINE("OFFLINE", "离线");

    private final String code;
    private final String label;

    RobotFlightStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(String code) {
        if (code == null) {
            return "未知";
        }
        return switch (code.toUpperCase()) {
            case "MISSION" -> "飞行中";
            case "RTH" -> "返航";
            case "IDLE", "STANDBY" -> "待机";
            case "DONE", "LANDING" -> "降落";
            default -> code;
        };
    }

    public static String normalizePhase(String phase) {
        if (phase == null) {
            return STANDBY.code;
        }
        return switch (phase.toLowerCase()) {
            case "mission" -> FLYING.code;
            case "rth" -> RTH.code;
            case "done" -> LANDING.code;
            case "idle" -> STANDBY.code;
            default -> phase.toUpperCase();
        };
    }
}
