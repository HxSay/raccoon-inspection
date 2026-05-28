package com.raccoon.cloud.drone.llm.service;

import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.PhotoWaypoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.llm.catalog.PatrolDeviceWaypointResolver;
import com.raccoon.cloud.drone.llm.catalog.PatrolDeviceWaypointResolver.ResolvedTowerVisit;
import com.raccoon.cloud.drone.llm.catalog.PatrolSceneGeometry;
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

    @Autowired
    private PatrolDeviceWaypointResolver patrolWaypointResolver;

    public InspectionTask convert(CheckResult checkResult) {
        Long mapId = checkResult.getResolvedMapId() != null
                ? checkResult.getResolvedMapId() : properties.getDefaultMapId();
        Long uavId = checkResult.getResolvedUavId() != null
                ? checkResult.getResolvedUavId() : properties.getDefaultUavId();
        List<UavInspectionDevice> devices = checkResult.getResolvedDevices();

        RouteBuild route;
        if (patrolWaypointResolver.supportsPatrolMap(mapId)) {
            List<ResolvedTowerVisit> visits = patrolWaypointResolver.resolveVisits(devices);
            if (!visits.isEmpty()) {
                route = buildPatrolTowerRoute(visits);
            } else {
                route = buildDefaultVerticalRoute();
            }
        } else {
            route = buildDefaultVerticalRoute();
        }

        InspectionTask task = new InspectionTask();
        task.setPlanId(properties.getDefaultPlanId());
        task.setTaskId(properties.getDefaultTaskId());
        task.setMapId(mapId);
        task.setUavId(uavId);
        task.setAlgorithm(properties.getDefaultAlgorithm());
        task.setTakeoff(route.takeoff());
        task.setLanding(route.landing());
        task.setWaypoints(route.waypoints());
        task.setPhotoWaypoints(route.photoWaypoints());
        task.setEstimated(route.estimated());

        log.info("已转换为 InspectionTask planId={} taskId={} mapId={} uavId={} waypoints={} photos={}",
                task.getPlanId(), task.getTaskId(), task.getMapId(), task.getUavId(),
                route.waypoints().size(), route.photoWaypoints().size());
        return task;
    }

    /**
     * 机巢起飞 → 爬升 → 逐基杆塔拍照 → 返航降落（经纬度随杆塔变化，与仿真场景对齐）。
     */
    private RouteBuild buildPatrolTowerRoute(List<ResolvedTowerVisit> visits) {
        GeoPoint takeoff = PatrolSceneGeometry.nestTakeoff();
        GeoPoint transit = PatrolSceneGeometry.nestTransit();
        GeoPoint landing = PatrolSceneGeometry.nestTakeoff();

        List<GeoPoint> waypoints = new ArrayList<>();
        waypoints.add(takeoff);
        waypoints.add(transit);

        List<PhotoWaypoint> photoWaypoints = new ArrayList<>();
        for (ResolvedTowerVisit visit : visits) {
            waypoints.add(visit.photoPoint());
            PhotoWaypoint photo = new PhotoWaypoint();
            photo.setLongitude(visit.photoPoint().getLongitude());
            photo.setLatitude(visit.photoPoint().getLatitude());
            photo.setHeight(visit.photoPoint().getHeight());
            /** 途经点下标（0=起飞后第一途经点 transit，1=第一基杆塔…） */
            photo.setWaypointIndex(waypoints.size() - 2);
            photo.setDeviceIds(List.of(visit.deviceId()));
            photoWaypoints.add(photo);
        }

        waypoints.add(landing);

        return new RouteBuild(takeoff, landing, waypoints, photoWaypoints, estimate(waypoints));
    }

    /** 旧逻辑：同经纬度仅高度差（仅作非输电场景兜底） */
    private RouteBuild buildDefaultVerticalRoute() {
        GeoPoint takeoff = point(properties.getDefaultLongitude(), properties.getDefaultLatitude(),
                properties.getDefaultTakeoffHeight());
        GeoPoint cruise = point(properties.getDefaultLongitude(), properties.getDefaultLatitude(),
                properties.getDefaultCruiseHeight());
        List<GeoPoint> waypoints = new ArrayList<>();
        waypoints.add(takeoff);
        waypoints.add(cruise);

        PhotoWaypoint photo = new PhotoWaypoint();
        photo.setLongitude(cruise.getLongitude());
        photo.setLatitude(cruise.getLatitude());
        photo.setHeight(cruise.getHeight());
        photo.setWaypointIndex(0);
        photo.setDeviceIds(List.of());

        return new RouteBuild(takeoff, takeoff, waypoints, List.of(photo), estimate(waypoints));
    }

    private InspectionTask.Estimated estimate(List<GeoPoint> waypoints) {
        double distanceM = GeoPathUtils.totalPathLengthMeters(waypoints);
        InspectionTask.Estimated est = new InspectionTask.Estimated();
        est.setDistanceM(distanceM);
        est.setDurationSec((int) Math.ceil(distanceM / CRUISE_SPEED_MPS));
        est.setBatteryPct((float) Math.min(100.0, (distanceM / 1000.0) * BATTERY_PCT_PER_KM));
        return est;
    }

    private GeoPoint point(double lng, double lat, double h) {
        return new GeoPoint(lng, lat, h);
    }

    private record RouteBuild(
            GeoPoint takeoff,
            GeoPoint landing,
            List<GeoPoint> waypoints,
            List<PhotoWaypoint> photoWaypoints,
            InspectionTask.Estimated estimated
    ) {
    }
}
