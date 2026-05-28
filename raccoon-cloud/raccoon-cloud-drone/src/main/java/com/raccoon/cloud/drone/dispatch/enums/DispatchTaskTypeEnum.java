package com.raccoon.cloud.drone.dispatch.enums;

import lombok.Getter;

/**
 * 调度任务类型枚举。
 * <p>用于优先级映射、能力匹配兜底；新增类型仅需追加，分配/规划逻辑无需改动。</p>
 *
 * @author raccoon
 */
@Getter
public enum DispatchTaskTypeEnum {

    /** 常规巡检：周期性巡检任务，优先级最低 */
    REGULAR("REGULAR", "常规巡检", 0.2f),

    /** 复检：上次巡检发现疑似缺陷的回访 */
    RE_INSPECTION("RE_INSPECTION", "复检任务", 0.5f),

    /** 临时任务：人工临时下发，介于常规与紧急之间 */
    TEMP("TEMP", "临时任务", 0.6f),

    /** 故障维修：发现明确故障后派单 */
    FAULT_REPAIR("FAULT_REPAIR", "故障维修", 0.85f),

    /** 应急：极端高优先级，跳过排队直接抢占 */
    EMERGENCY("EMERGENCY", "应急任务", 1.0f);

    private final String code;
    private final String label;
    /** 任务类型固有优先级（0~1），用于 TaskPriorityService 兜底归一化 */
    private final float intrinsicWeight;

    DispatchTaskTypeEnum(String code, String label, float intrinsicWeight) {
        this.code = code;
        this.label = label;
        this.intrinsicWeight = intrinsicWeight;
    }

    /**
     * 安全解析任务类型，未匹配时回退为 {@link #REGULAR}。
     *
     * @param code 任意大小写的字符串
     * @return 对应枚举
     */
    public static DispatchTaskTypeEnum parse(String code) {
        if (code == null || code.isBlank()) {
            return REGULAR;
        }
        String upper = code.trim().toUpperCase();
        for (DispatchTaskTypeEnum e : values()) {
            if (e.code.equals(upper)) {
                return e;
            }
        }
        return REGULAR;
    }
}
