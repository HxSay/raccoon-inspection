package com.raccoon.cloud.system.cmms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单条巡检任务手动派发巡检工单。
 */
@Data
public class InspectionTaskDispatchRequest {
    @NotNull
    private Long taskId;
}
