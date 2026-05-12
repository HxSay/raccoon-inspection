package com.raccoon.cloud.system.cmms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InspectionStepExecuteRequest {
    @NotNull
    private Long detailId;
    private String remark;
}
