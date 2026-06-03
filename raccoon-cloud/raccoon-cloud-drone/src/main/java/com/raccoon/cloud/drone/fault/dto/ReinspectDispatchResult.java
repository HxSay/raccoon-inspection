package com.raccoon.cloud.drone.fault.dto;

import lombok.Data;

@Data
public class ReinspectDispatchResult {

    private boolean success;
    private String reinspectTaskId;
    private Long assignedTerminalId;
    private String message;

    public static ReinspectDispatchResult success(String taskId, Long terminalId) {
        ReinspectDispatchResult r = new ReinspectDispatchResult();
        r.setSuccess(true);
        r.setReinspectTaskId(taskId);
        r.setAssignedTerminalId(terminalId);
        return r;
    }

    public static ReinspectDispatchResult failed(String message) {
        ReinspectDispatchResult r = new ReinspectDispatchResult();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
