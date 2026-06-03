package com.raccoon.cloud.drone.fault.service;

import com.raccoon.cloud.drone.fault.dto.FaultResponsePlan;
import com.raccoon.cloud.drone.fault.enums.FaultAction;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class FaultResponsePlanService {

    public FaultResponsePlan buildPlan(FaultEvent event, FaultLevel level) {
        FaultResponsePlan plan = new FaultResponsePlan();
        plan.setEventId(event.getEventId());
        plan.setLevel(level);
        switch (level) {
            case GENERAL -> {
                plan.setAction(FaultAction.SCHEDULED_REINSPECT);
                plan.setReinspectDeadline(LocalDateTime.now().plusHours(24));
                plan.setExpandScope(false);
                plan.setExpandDepth(0);
                plan.setPauseOtherTasks(false);
            }
            case SERIOUS -> {
                plan.setAction(FaultAction.IMMEDIATE_REINSPECT);
                plan.setReinspectDeadline(LocalDateTime.now().plusMinutes(15));
                plan.setExpandScope(true);
                plan.setExpandDepth(2);
                plan.setRequireMultiModal(true);
                plan.setPauseOtherTasks(false);
            }
            case CRITICAL -> {
                plan.setAction(FaultAction.EMERGENCY_SWEEP);
                plan.setReinspectDeadline(LocalDateTime.now().plusMinutes(3));
                plan.setExpandScope(true);
                plan.setExpandDepth(3);
                plan.setExpandAreaRadiusM(500.0);
                plan.setRequireMultiModal(true);
                plan.setPauseOtherTasks(true);
                plan.setTriggerEmergencyPlan(true);
            }
        }
        return plan;
    }
}
