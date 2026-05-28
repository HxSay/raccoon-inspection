package com.raccoon.cloud.drone.dispatch.enums;

import lombok.Getter;

/**
 * 调度任务生命周期状态机。
 * <p>状态流转：CREATED → PARSED → PRIORITIZED → ASSIGNED → SIMULATED → PLANNED → DISPATCHED → EXECUTING → COMPLETED；
 * 任何阶段失败均收敛到 FAILED。</p>
 *
 * @author raccoon
 */
@Getter
public enum DispatchTaskStatusEnum {

    /** 已创建，未解析 */
    CREATED("CREATED", "已创建"),
    /** 任务解析完成（含范围/设备 ID） */
    PARSED("PARSED", "已解析"),
    /** 已完成优先级评分 */
    PRIORITIZED("PRIORITIZED", "已评分"),
    /** 已分配到终端 */
    ASSIGNED("ASSIGNED", "已分配"),
    /** 已通过数字孪生预演 */
    SIMULATED("SIMULATED", "已预演"),
    /** 已规划全局路径 */
    PLANNED("PLANNED", "已规划"),
    /** 已下发到边缘执行 Agent */
    DISPATCHED("DISPATCHED", "已下发"),
    /** 执行中 */
    EXECUTING("EXECUTING", "执行中"),
    /** 已完成 */
    COMPLETED("COMPLETED", "已完成"),
    /** 失败 */
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;

    DispatchTaskStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
