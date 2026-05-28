package com.raccoon.cloud.drone.llm.service;

import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.PhotoWaypoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.llm.config.LlmTaskParseProperties;
import com.raccoon.cloud.drone.llm.model.InspectionTask;
import com.raccoon.cloud.drone.llm.service.ResultCheckAndFillService.CheckResult;
import com.raccoon.cloud.drone.util.GeoPathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 将槽位结果转换为标准化内部 InspectionTask（下发 JSON）。
 */
@Slf4j
@Service
public class LlmResultConvertService {

    private static final double CRUISE_SPEED_MPS = 8.0;
    private static final double BATTERY_PCT_PER_KM = 12.0;

    @Autowired
    private LlmTaskParseProperties properties;

    public InspectionTask convert(CheckResult checkResult) {
        Long mapId = checkResult.getResolvedMapId() != null
                ? checkResult.getResolvedMapId() : properties.getDefaultMapId();
        Long uavId = checkResult.getResolvedUavId() != null
                ? checkResult.getResolvedUavId() : properties.getDefaultUavId();
        List<UavInspectionDevice> devices = checkResult.getResolvedDevices();

        GeoPoint takeoff = point(properties.getDefaultLongitude(), properties.getDefaultLatitude(),
                properties.getDefaultTakeoffHeight());
        GeoPoint cruise = point(properties.getDefaultLongitude(), properties.getDefaultLatitude(),
                properties.getDefaultCruiseHeight());

        List<GeoPoint> waypoints = new ArrayList<>();
        waypoints.add(takeoff);
        waypoints.add(cruise);

        List<Long> deviceIds = new ArrayList<>();
        for (UavInspectionDevice d : devices) {
            deviceIds.add(d.getId());
        }

        PhotoWaypoint photo = new PhotoWaypoint();
        photo.setLongitude(cruise.getLongitude());
        photo.setLatitude(cruise.getLatitude());
        photo.setHeight(cruise.getHeight());
        photo.setWaypointIndex(0);
        photo.setDeviceIds(deviceIds);

        InspectionTask task = new InspectionTask();
        task.setPlanId(properties.getDefaultPlanId());
        task.setTaskId(properties.getDefaultTaskId());
        task.setMapId(mapId);
        task.setUavId(uavId);
        task.setAlgorithm(properties.getDefaultAlgorithm());
        task.setTakeoff(takeoff);
        task.setLanding(takeoff);
        task.setWaypoints(waypoints);
        task.setPhotoWaypoints(List.of(photo));

        double distanceM = GeoPathUtils.totalPathLengthMeters(waypoints);
        InspectionTask.Estimated est = new InspectionTask.Estimated();
        est.setDistanceM(distanceM);
        est.setDurationSec((int) Math.ceil(distanceM / CRUISE_SPEED_MPS));
        est.setBatteryPct((float) Math.min(100.0, (distanceM / 1000.0) * BATTERY_PCT_PER_KM));
        task.setEstimated(est);

        log.info("已转换为 InspectionTask planId={} taskId={} mapId={} uavId={} devices={}",
                task.getPlanId(), task.getTaskId(), task.getMapId(), task.getUavId(), deviceIds.size());
        return task;
    }

    private GeoPoint point(double lng, double lat, double h) {
        return new GeoPoint(lng, lat, h);
    }
}
