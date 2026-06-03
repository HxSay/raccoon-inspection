package com.raccoon.cloud.drone.fault.dto;

import com.raccoon.cloud.drone.fault.enums.FaultHandleStatus;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import lombok.Data;

@Data
public class FaultHandleResult {

    private String eventId;
    private FaultHandleStatus status;
    private FaultLevel level;
    private String message;
    private FaultResponsePlan plan;
    private ExpandedScope expandedScope;
    private ReinspectDispatchResult dispatch;

    public static FaultHandleResult merged(String eventId) {
        FaultHandleResult r = new FaultHandleResult();
        r.setEventId(eventId);
        r.setStatus(FaultHandleStatus.MERGED);
        r.setMessage("5分钟内同设备同类型异常已合并");
        return r;
    }
}
