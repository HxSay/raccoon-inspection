package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionPlanPageRequest extends CmmsPageRequest {
    private String planName;
    private Integer status;
}
