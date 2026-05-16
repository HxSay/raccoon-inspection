package com.raccoon.cloud.drone.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 拍照航点：绑定途经航点 + 巡检设备；坐标由途经点解析后落库。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PhotoWaypoint extends GeoPoint {

    /** 绑定的途经航点下标（从 0 起，对应飞行路径中的途经段） */
    private Integer waypointIndex;

    /** 本航点需拍照/巡检的设备 ID 列表 */
    private List<Long> deviceIds = new ArrayList<>();
}
