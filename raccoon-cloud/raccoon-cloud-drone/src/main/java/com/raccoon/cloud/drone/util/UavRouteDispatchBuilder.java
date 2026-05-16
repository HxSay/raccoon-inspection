package com.raccoon.cloud.drone.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.PhotoWaypoint;
import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import com.raccoon.cloud.drone.entity.UavRoutePlan;

import java.util.Collections;
import java.util.List;

public final class UavRouteDispatchBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UavRouteDispatchBuilder() {
    }

    public static UavRouteDispatchPayload fromPlan(UavRoutePlan plan) {
        UavRouteDispatchPayload payload = new UavRouteDispatchPayload();
        payload.setPlanId(plan.getId());
        payload.setTaskId(plan.getTaskId());
        payload.setMapId(plan.getMapId());
        payload.setUavId(plan.getUavId());
        payload.setAlgorithm(plan.getAlgorithm());

        List<GeoPoint> path = parseList(plan.getPathPoints(), new TypeReference<List<GeoPoint>>() {});
        List<GeoPoint> fullPath =
                path.isEmpty() ? List.of(parsePoint(plan.getStartPoint()), parsePoint(plan.getEndPoint())) : path;
        payload.setTakeoff(fullPath.isEmpty() ? parsePoint(plan.getStartPoint()) : fullPath.get(0));
        payload.setLanding(
                fullPath.isEmpty() ? parsePoint(plan.getEndPoint()) : fullPath.get(fullPath.size() - 1));
        /** 飞行点序列与 path_points 完整路径一致（含起降点） */
        payload.setWaypoints(fullPath);
        List<PhotoWaypoint> photoWaypoints = PhotoWaypointUtils.resolveCoordinates(
                parseList(plan.getPhotoPoints(), new TypeReference<List<PhotoWaypoint>>() {}), fullPath);
        payload.setPhotoWaypoints(photoWaypoints);
        if (PhotoWaypointUtils.hasBoundDevices(photoWaypoints)) {
            payload.setDeviceVisitOrder(PhotoWaypointUtils.flattenDeviceVisitOrder(photoWaypoints));
        } else {
            payload.setDeviceVisitOrder(parseList(plan.getVisitOrder(), new TypeReference<List<Long>>() {}));
        }

        UavRouteDispatchPayload.Estimated est = new UavRouteDispatchPayload.Estimated();
        est.setDistanceM(plan.getTotalDistance());
        est.setDurationSec(plan.getEstimatedTime());
        est.setBatteryPct(plan.getEstimatedBattery());
        payload.setEstimated(est);
        return payload;
    }

    private static GeoPoint parsePoint(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return PointStringParser.parseLngLatHeight(raw);
    }

    private static <T> List<T> parseList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<T> list = MAPPER.readValue(json, type);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
