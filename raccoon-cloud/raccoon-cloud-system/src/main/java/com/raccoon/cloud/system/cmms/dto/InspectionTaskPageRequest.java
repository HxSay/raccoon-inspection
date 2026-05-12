package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionTaskPageRequest extends CmmsPageRequest {
    private String taskCode;
    private Integer status;
    private Long deviceId;
    private Long execUserId;
}
