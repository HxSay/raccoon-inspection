package com.raccoon.cloud.drone.dto;

import lombok.Data;

import java.util.List;

/**
 * 下发给无人机的任务 JSON 结构
 */
@Data
public class UavRouteDispatchPayload {

    private Long planId;
    private Long taskId;
    private Long mapId;
    private Long uavId;
    private String algorithm;

    private GeoPoint takeoff;
    private GeoPoint landing;

    private List<GeoPoint> waypoints;
    private List<GeoPoint> photoWaypoints;
    private List<Long> deviceVisitOrder;

    private Estimated estimated;

    @Data
    public static class Estimated {
        private Double distanceM;
        private Integer durationSec;
        private Float batteryPct;
    }
}
