package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderPageRequest extends CmmsPageRequest {
    private String orderCode;
    private Integer status;
    private Long deviceId;
    /** 维修员：指派给我 */
    private Long assignUserId;
    /** 上报人 */
    private Long reportUserId;
}
