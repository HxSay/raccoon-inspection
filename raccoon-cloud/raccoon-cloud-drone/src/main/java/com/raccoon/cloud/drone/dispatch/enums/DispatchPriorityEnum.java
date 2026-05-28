package com.raccoon.cloud.drone.dispatch.enums;

import lombok.Getter;

/**
 * 调度优先级枚举（五档）。
 * <p>{@link #fromScore(double)} 将 0~1 归一化得分映射到优先级档位，供 Agent 决策与排队使用。</p>
 *
 * @author raccoon
 */
@Getter
public enum DispatchPriorityEnum {

    LOW("LOW", "低", 1, 0.00, 0.20),
    NORMAL("NORMAL", "中", 2, 0.20, 0.50),
    HIGH("HIGH", "高", 3, 0.50, 0.75),
    URGENT("URGENT", "紧急", 4, 0.75, 0.90),
    CRITICAL("CRITICAL", "极紧急", 5, 0.90, 1.01);

    private final String code;
    private final String label;
    /** 排序权重，数值越大优先级越高 */
    private final int weight;
    /** 区间下界（含） */
    private final double lower;
    /** 区间上界（不含） */
    private final double upper;

    DispatchPriorityEnum(String code, String label, int weight, double lower, double upper) {
        this.code = code;
        this.label = label;
        this.weight = weight;
        this.lower = lower;
        this.upper = upper;
    }

    /**
     * 将 [0,1] 归一化得分映射为优先级档。
     * <p>越界值会截断到 [0,1]，保证非空返回。</p>
     *
     * @param score 0~1 得分
     * @return 命中的档位
     */
    public static DispatchPriorityEnum fromScore(double score) {
        double s = Math.max(0.0, Math.min(1.0, score));
        for (DispatchPriorityEnum e : values()) {
            if (s >= e.lower && s < e.upper) {
                return e;
            }
        }
        return CRITICAL;
    }

    /**
     * 兼容旧解析模块的二档优先级（NORMAL/URGENT）。
     *
     * @param legacy 字符串
     * @return 对应五档枚举
     */
    public static DispatchPriorityEnum fromLegacy(String legacy) {
        if (legacy == null) {
            return NORMAL;
        }
        return switch (legacy.trim().toUpperCase()) {
            case "URGENT" -> URGENT;
            case "CRITICAL" -> CRITICAL;
            case "HIGH" -> HIGH;
            case "LOW" -> LOW;
            default -> NORMAL;
        };
    }
}
