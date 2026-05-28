package com.raccoon.cloud.drone.dispatch.enums;

import lombok.Getter;

/**
 * 终端能力等级，根据运动 / 感知 / 续航三大维度综合量化结果归档。
 *
 * @author raccoon
 */
@Getter
public enum TerminalCapabilityLevelEnum {

    /** 高能力：满足全场景巡检需求 */
    HIGH("HIGH", 0.75),
    /** 中等：适用于常规巡检 */
    MEDIUM("MEDIUM", 0.45),
    /** 低能力：仅支持轻量巡检 */
    LOW("LOW", 0.0);

    private final String code;
    /** 综合得分下限（含） */
    private final double lowerBound;

    TerminalCapabilityLevelEnum(String code, double lowerBound) {
        this.code = code;
        this.lowerBound = lowerBound;
    }

    /**
     * 按综合得分映射等级（截断到 [0,1]）。
     *
     * @param score 综合能力得分
     * @return 能力等级
     */
    public static TerminalCapabilityLevelEnum fromScore(double score) {
        double s = Math.max(0.0, Math.min(1.0, score));
        if (s >= HIGH.lowerBound) {
            return HIGH;
        }
        if (s >= MEDIUM.lowerBound) {
            return MEDIUM;
        }
        return LOW;
    }
}
