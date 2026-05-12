package com.raccoon.cloud.system.cmms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InspectionStepCollectRequest {
    @NotNull
    private Long detailId;
    @NotBlank
    private String actualValue;
    private String photoUrl;
    private String remark;
}
