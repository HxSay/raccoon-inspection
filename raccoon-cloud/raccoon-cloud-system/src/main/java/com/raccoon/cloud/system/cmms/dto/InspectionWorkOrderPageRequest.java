package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 巡检工单分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionWorkOrderPageRequest extends CmmsPageRequest {
    private String area;
    /** 班次 1早 2中 3晚 */
    private Integer shiftType;
    /** 主表状态 */
    private Integer status;
    private Long planId;
    private Long taskId;
    private String planStartFrom;
    private String planStartTo;
}
