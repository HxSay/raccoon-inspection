package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionDeptPageRequest extends CmmsPageRequest {
    private String deptName;
}
