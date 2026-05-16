package com.raccoon.cloud.drone.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.dto.GeoPoint;
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
        payload.setTakeoff(path.isEmpty() ? parsePoint(plan.getStartPoint()) : path.get(0));
        payload.setLanding(path.isEmpty() ? parsePoint(plan.getEndPoint()) : path.get(path.size() - 1));
        payload.setWaypoints(path);
        payload.setPhotoWaypoints(parseList(plan.getPhotoPoints(), new TypeReference<List<GeoPoint>>() {}));
        payload.setDeviceVisitOrder(parseList(plan.getVisitOrder(), new TypeReference<List<Long>>() {}));

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
