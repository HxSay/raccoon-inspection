package com.raccoon.cloud.drone.util;

import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.PhotoWaypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PhotoWaypointUtils {

    private PhotoWaypointUtils() {
    }

    /** 从完整路径中提取途经航点（不含起降点） */
    public static List<GeoPoint> extractViaPoints(List<GeoPoint> fullPath) {
        if (fullPath == null || fullPath.size() <= 2) {
            return Collections.emptyList();
        }
        return new ArrayList<>(fullPath.subList(1, fullPath.size() - 1));
    }

    /**
     * 按途经航点下标解析拍照点坐标；兼容旧数据（仅含经纬高、无 waypointIndex）。
     */
    public static List<PhotoWaypoint> resolveCoordinates(List<PhotoWaypoint> photoWaypoints, List<GeoPoint> fullPath) {
        if (photoWaypoints == null || photoWaypoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<GeoPoint> viaPoints = extractViaPoints(fullPath);
        List<PhotoWaypoint> resolved = new ArrayList<>();
        for (PhotoWaypoint raw : photoWaypoints) {
            if (raw == null) {
                continue;
            }
            PhotoWaypoint wp = copyMeta(raw);
            if (raw.getWaypointIndex() != null) {
                int idx = raw.getWaypointIndex();
                if (idx < 0 || idx >= viaPoints.size()) {
                    throw new IllegalArgumentException(
                            "拍照航点绑定的途经点下标无效: " + idx + "，当前途经点数量为 " + viaPoints.size());
                }
                GeoPoint via = viaPoints.get(idx);
                wp.setLongitude(via.getLongitude());
                wp.setLatitude(via.getLatitude());
                wp.setHeight(via.getHeight());
            } else if (raw.getLongitude() != null && raw.getLatitude() != null && raw.getHeight() != null) {
                wp.setLongitude(raw.getLongitude());
                wp.setLatitude(raw.getLatitude());
                wp.setHeight(raw.getHeight());
            } else {
                throw new IllegalArgumentException("拍照航点须绑定途经航点（waypointIndex）");
            }
            resolved.add(wp);
        }
        return resolved;
    }

    private static PhotoWaypoint copyMeta(PhotoWaypoint raw) {
        PhotoWaypoint wp = new PhotoWaypoint();
        wp.setWaypointIndex(raw.getWaypointIndex());
        wp.setDeviceIds(raw.getDeviceIds() == null ? new ArrayList<>() : new ArrayList<>(raw.getDeviceIds()));
        return wp;
    }

    /** 按拍照航点顺序展开设备访问顺序 */
    public static List<Long> flattenDeviceVisitOrder(List<PhotoWaypoint> photoWaypoints) {
        if (photoWaypoints == null || photoWaypoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> order = new ArrayList<>();
        for (PhotoWaypoint wp : photoWaypoints) {
            if (wp == null || wp.getDeviceIds() == null) {
                continue;
            }
            for (Long id : wp.getDeviceIds()) {
                if (id != null) {
                    order.add(id);
                }
            }
        }
        return order;
    }

    public static boolean hasBoundDevices(List<PhotoWaypoint> photoWaypoints) {
        return !flattenDeviceVisitOrder(photoWaypoints).isEmpty();
    }
}
