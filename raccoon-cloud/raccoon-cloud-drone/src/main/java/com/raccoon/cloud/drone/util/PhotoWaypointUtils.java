package com.raccoon.cloud.drone.util;

import com.raccoon.cloud.drone.dto.PhotoWaypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PhotoWaypointUtils {

    private PhotoWaypointUtils() {
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
