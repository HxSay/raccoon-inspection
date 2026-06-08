package com.raccoon.cloud.drone.closeloop.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 实际完成的巡检点位（由边缘上报的拍照/多模态采样聚合而来）。
 */
@Data
public class CapturedPointDTO {

    /** 航点序号（与计划航点对齐） */
    private Integer waypointIndex;
    /** 关联设备 ID（可空） */
    private Long deviceId;
    /** 该点位实际采集到的多模态类型：VISIBLE/THERMAL/AUDIO/VIBRATION/TEMPERATURE */
    private List<String> dataTypes = new ArrayList<>();
    /** AI 是否检出缺陷 */
    private boolean hasDefect;
    /** AI 标签 */
    private String faultLabel;
}
