package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceInfoPageRequest extends CmmsPageRequest {
    private String deviceName;
    private String deviceCode;
    private Integer status;
    private Long categoryId;
}
