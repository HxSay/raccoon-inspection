package com.raccoon.cloud.drone.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 拍照航点：坐标 + 绑定的巡检设备（可多选）
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PhotoWaypoint extends GeoPoint {

    /** 本航点需拍照/巡检的设备 ID 列表 */
    private List<Long> deviceIds = new ArrayList<>();
}
