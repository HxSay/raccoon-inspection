package com.raccoon.common.dto.planning;

import lombok.Data;

/**
 * 工单审核通过后的正式下发结果（含仿真航线载荷）。
 */
@Data
public class WorkOrderAuditApproveResult {

    private boolean dispatched;
    private String message;
    /** 与 drone {@code UavRouteDispatchPayload} 结构一致的 JSON 对象 */
    private Object dispatchPayload;
}
