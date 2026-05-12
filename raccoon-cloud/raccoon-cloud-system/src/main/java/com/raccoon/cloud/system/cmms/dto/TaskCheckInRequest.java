package com.raccoon.cloud.system.cmms.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 移动端现场签到：写入 inspection_task 的 longitude、latitude。
 */
@Data
public class TaskCheckInRequest {
    private Long taskId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    /** 扫码得到的设备编号，可选；若传则须与任务设备一致 */
    private String scannedDeviceCode;
}
