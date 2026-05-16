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
    /** 拍照航点及每点绑定的巡检设备 */
    private List<PhotoWaypoint> photoWaypoints;
    /** 由拍照航点绑定顺序展开的设备访问顺序（兼容旧消费方） */
    private List<Long> deviceVisitOrder;

    private Estimated estimated;

    @Data
    public static class Estimated {
        private Double distanceM;
        private Integer durationSec;
        private Float batteryPct;
    }
}
