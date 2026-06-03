package com.raccoon.cloud.drone.fault.enums;

import lombok.Getter;

/**
 * 故障分级：一般 / 严重 / 紧急（与 PDF 2.1 对齐）。
 */
@Getter
public enum FaultLevel {

    GENERAL("GENERAL", "一般", 1),
    SERIOUS("SERIOUS", "严重", 2),
    CRITICAL("CRITICAL", "紧急", 3);

    private final String code;
    private final String label;
    private final int weight;

    FaultLevel(String code, String label, int weight) {
        this.code = code;
        this.label = label;
        this.weight = weight;
    }

    public static FaultLevel parse(String code) {
        if (code == null || code.isBlank()) {
            return GENERAL;
        }
        String upper = code.trim().toUpperCase();
        for (FaultLevel e : values()) {
            if (e.code.equals(upper)) {
                return e;
            }
        }
        return GENERAL;
    }
}
