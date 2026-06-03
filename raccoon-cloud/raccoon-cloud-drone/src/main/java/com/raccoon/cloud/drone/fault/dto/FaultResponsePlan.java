package com.raccoon.cloud.drone.fault.dto;

import com.raccoon.cloud.drone.fault.enums.FaultAction;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FaultResponsePlan {

    private String eventId;
    private FaultLevel level;
    private FaultAction action;
    private LocalDateTime reinspectDeadline;
    private boolean expandScope;
    private int expandDepth;
    private boolean requireMultiModal;
    private boolean pauseOtherTasks;
    private Double expandAreaRadiusM;
    private boolean triggerEmergencyPlan;
}
