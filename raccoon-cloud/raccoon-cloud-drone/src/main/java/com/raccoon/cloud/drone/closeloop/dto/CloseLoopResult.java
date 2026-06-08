package com.raccoon.cloud.drone.closeloop.dto;

import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import lombok.Data;

/**
 * 任务闭环受理总响应。
 */
@Data
public class CloseLoopResult {

    private boolean success;
    private String taskId;
    /** COMPLETED / PARTIAL / MANUAL_REQUIRED / COLLECTING */
    private String closeStatus;
    private double completionRate;

    private InspectionReportVO report;
    private IntegrityVerifyResult verify;

    /** 是否触发补检 */
    private boolean rescheduled;
    private String reinspectTaskId;
    /** 补检航线，前端可直接下发仿真补飞 */
    private UavRouteDispatchPayload reinspectRoutePayload;

    private String message;

    public static CloseLoopResult fail(String taskId, String message) {
        CloseLoopResult r = new CloseLoopResult();
        r.setSuccess(false);
        r.setTaskId(taskId);
        r.setMessage(message);
        return r;
    }
}
