package com.raccoon.cloud.system.cmms.constants;

/**
 * 巡检工单主表状态。
 */
public final class InspectionWorkOrderStatus {

    private InspectionWorkOrderStatus() {
    }

    /** 待下发 */
    public static final int PENDING_ISSUE = 1;
    /** 待执行 */
    public static final int PENDING_EXEC = 2;
    /** 执行中 */
    public static final int RUNNING = 3;
    /** 已完成 */
    public static final int FINISHED = 4;
    /** 已取消 */
    public static final int CANCELLED = 5;
}
