package com.raccoon.common.dto.planning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlanningEndToEndResponse {

    private boolean success;
    private String message;

    private boolean needFollowUp;
    private String followUpQuestion;

    private String dispatchTaskId;
    private Long assignedTerminalId;
    private String assignedTerminalName;
    private String assignReason;

    private Long planId;
    private Long taskId;
    private Long workOrderId;
    private String orderNo;
    private Integer workOrderStatus;
    private LocalDateTime auditDeadline;
}
