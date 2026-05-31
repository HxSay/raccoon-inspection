package com.raccoon.common.dto.planning;

import lombok.Data;

@Data
public class WorkOrderAuditActionRequest {

    private Long workOrderId;
    private Long auditorId;
    private String auditorName;
    /** 驳回时必填 */
    private String rejectReason;
}
