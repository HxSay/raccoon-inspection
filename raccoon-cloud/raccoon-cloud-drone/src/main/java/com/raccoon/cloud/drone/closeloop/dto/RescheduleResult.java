package com.raccoon.cloud.drone.closeloop.dto;

import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 未完成项重调度结果（步骤5 输出）。
 */
@Data
public class RescheduleResult {

    private boolean needed;
    private boolean success;
    private String reinspectTaskId;
    private Long assignedTerminalId;
    private List<Long> missedDeviceIds = new ArrayList<>();
    private String message;
    /** 可下发仿真的补检航线 */
    private UavRouteDispatchPayload routePayload;

    public static RescheduleResult notNeeded() {
        RescheduleResult r = new RescheduleResult();
        r.setNeeded(false);
        r.setSuccess(true);
        r.setMessage("核对通过，无需补检");
        return r;
    }
}
