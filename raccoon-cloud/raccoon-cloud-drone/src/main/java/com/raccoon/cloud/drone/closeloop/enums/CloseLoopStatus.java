package com.raccoon.cloud.drone.closeloop.enums;

import lombok.Getter;

/**
 * 任务闭环状态机：回收 → 核对 → 判定。
 */
@Getter
public enum CloseLoopStatus {

    /** 结果回收中（部分终端未上报） */
    COLLECTING("COLLECTING", "回收中"),
    /** 完整闭环：点位/设备/数据/轨迹全部达标 */
    COMPLETED("COMPLETED", "已闭环"),
    /** 部分完成：存在漏检/数据缺口，已触发补检 */
    PARTIAL("PARTIAL", "部分完成·已补检"),
    /** 多次补检仍未达标，转人工 */
    MANUAL_REQUIRED("MANUAL_REQUIRED", "需人工介入");

    private final String code;
    private final String label;

    CloseLoopStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
