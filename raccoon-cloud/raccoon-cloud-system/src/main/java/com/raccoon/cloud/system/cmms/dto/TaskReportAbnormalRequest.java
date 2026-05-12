package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;

/**
 * 巡检异常上报：生成维修工单 work_order（source=1 巡检上报）。
 */
@Data
public class TaskReportAbnormalRequest {
    private Long taskId;
    private String faultDescription;
    /** JSON 数组字符串，与表 fault_image_urls 一致 */
    private String faultImageUrls;
}
