package com.raccoon.cloud.system.cmms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 drone 路径规划 JSON（GlobalPathPlan 快照）。
 */
public final class AgentPathPlanHelper {

    private AgentPathPlanHelper() {
    }

    @Data
    public static class PhotoAnchor {
        private int waypointIndex;
        private Double longitude;
        private Double latitude;
        private Double height;
        private int deviceOrder;
    }

    public static List<PhotoAnchor> parsePhotoAnchors(String pathPlanJson, ObjectMapper mapper) {
        List<PhotoAnchor> out = new ArrayList<>();
        if (!StringUtils.hasText(pathPlanJson) || mapper == null) {
            return out;
        }
        try {
            JsonNode root = mapper.readTree(pathPlanJson);
            JsonNode photos = root.get("photoWaypoints");
            if (photos == null || !photos.isArray()) {
                return out;
            }
            int i = 0;
            for (JsonNode p : photos) {
                PhotoAnchor a = new PhotoAnchor();
                a.setDeviceOrder(i++);
                if (p.has("waypointIndex")) {
                    a.setWaypointIndex(p.get("waypointIndex").asInt(0));
                }
                if (p.has("longitude")) {
                    a.setLongitude(p.get("longitude").asDouble());
                }
                if (p.has("latitude")) {
                    a.setLatitude(p.get("latitude").asDouble());
                }
                if (p.has("height")) {
                    a.setHeight(p.get("height").asDouble());
                }
                out.add(a);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return out;
    }
}
