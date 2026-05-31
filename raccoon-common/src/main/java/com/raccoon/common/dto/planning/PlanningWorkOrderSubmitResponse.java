package com.raccoon.common.dto.planning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlanningWorkOrderSubmitResponse {

    private Long planId;
    private Long taskId;
    private Long workOrderId;
    private String orderNo;
    /** 6=待审核 */
    private Integer status;
    private LocalDateTime auditDeadline;
    private String message;
}
