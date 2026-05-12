package com.raccoon.cloud.system.cmms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 按巡检计划向待执行任务派发巡检工单（每任务一单，并自动下发）。
 */
@Data
public class InspectionDispatchFromPlanRequest {
    @NotNull
    private Long planId;
}
