package com.raccoon.cloud.drone.util;

import com.raccoon.cloud.drone.dto.GeoPoint;

public final class PointStringParser {

    private PointStringParser() {
    }

    /**
     * 解析 "经度,纬度,高度" 或 "经度,纬度,高度米" 等逗号分隔三段。
     */
    public static GeoPoint parseLngLatHeight(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("坐标不能为空");
        }
        String[] p = raw.split(",");
        if (p.length < 3) {
            throw new IllegalArgumentException("坐标格式应为：经度,纬度,高度");
        }
        double lng = Double.parseDouble(p[0].trim());
        double lat = Double.parseDouble(p[1].trim());
        double h = Double.parseDouble(p[2].trim());
        return new GeoPoint(lng, lat, h);
    }
}
