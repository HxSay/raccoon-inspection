package com.raccoon.cloud.drone.closeloop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态数据缺口明细（步骤2 完整性核对）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataGap {
    private Integer waypointIndex;
    private Long deviceId;
    private String requiredType;
    private String message;
}
