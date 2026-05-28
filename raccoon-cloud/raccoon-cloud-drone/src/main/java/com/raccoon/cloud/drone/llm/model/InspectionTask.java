package com.raccoon.cloud.drone.llm.model;

import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.PhotoWaypoint;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准化内部巡检任务模型（与无人机下发 JSON 字段一致）。
 */
@Data
public class InspectionTask {

    private Long planId;
    private Long taskId;
    private Long mapId;
    private Long uavId;
    private String algorithm;

    private GeoPoint takeoff;
    private GeoPoint landing;

    private List<GeoPoint> waypoints = new ArrayList<>();
    private List<PhotoWaypoint> photoWaypoints = new ArrayList<>();

    private Estimated estimated;

    @Data
    public static class Estimated {
        private Double distanceM;
        private Integer durationSec;
        private Float batteryPct;
    }
}
