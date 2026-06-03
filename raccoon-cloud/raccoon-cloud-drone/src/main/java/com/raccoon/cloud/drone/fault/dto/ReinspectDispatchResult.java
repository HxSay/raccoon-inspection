package com.raccoon.cloud.drone.fault.dto;

import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import lombok.Data;

@Data
public class ReinspectDispatchResult {

    private boolean success;
    private String reinspectTaskId;
    private Long assignedTerminalId;
    private String message;
    /** 可下发仿真的航线 JSON（与 Agent 巡检一致） */
    private UavRouteDispatchPayload routePayload;
    /** 扩范围后纳入巡检的设备数 */
    private Integer expandedDeviceCount;

    public static ReinspectDispatchResult success(String taskId, Long terminalId,
                                                   UavRouteDispatchPayload routePayload, int deviceCount) {
        ReinspectDispatchResult r = new ReinspectDispatchResult();
        r.setSuccess(true);
        r.setReinspectTaskId(taskId);
        r.setAssignedTerminalId(terminalId);
        r.setRoutePayload(routePayload);
        r.setExpandedDeviceCount(deviceCount);
        return r;
    }

    public static ReinspectDispatchResult failed(String message) {
        ReinspectDispatchResult r = new ReinspectDispatchResult();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
