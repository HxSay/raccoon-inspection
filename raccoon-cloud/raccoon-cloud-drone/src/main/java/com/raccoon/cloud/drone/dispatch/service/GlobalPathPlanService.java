package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.algorithm.RrtStarPlanner;
import com.raccoon.cloud.drone.dispatch.algorithm.TrajectorySmoothing;
import com.raccoon.cloud.drone.dispatch.algorithm.TspOrderOptimizer;
import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskStatusEnum;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.GlobalPathPlan;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.PhotoWaypoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.llm.catalog.PatrolDeviceWaypointResolver;
import com.raccoon.cloud.drone.llm.catalog.PatrolSceneGeometry;
import com.raccoon.cloud.drone.util.GeoPathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局路径规划服务（步骤 7）。
 * <p>步骤：
 * <ol>
 *   <li>{@link TspOrderOptimizer} 对设备点位做最优访问顺序</li>
 *   <li>{@link RrtStarPlanner} 在禁飞区/障碍约束下规划三维路径</li>
 *   <li>{@link TrajectorySmoothing} 共线剔除 + 滑动均值平滑</li>
 *   <li>计算总航程、耗时、耗电估算</li>
 * </ol>
 *
 * @author raccoon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalPathPlanService {

    private final TspOrderOptimizer tspOptimizer;
    private final RrtStarPlanner rrtPlanner;
    private final TrajectorySmoothing smoothing;
    private final PatrolDeviceWaypointResolver patrolDeviceWaypointResolver;

    /**
     * 为单个已分配任务规划路径。
     *
     * @param task     任务（必须已 ASSIGNED）
     * @param terminal 任务对应终端
     * @return 路径规划结果，并写回 task.pathPlan
     */
    public GlobalPathPlan plan(DispatchInspectionTask task, TerminalState terminal) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        if (task.getAssignedTerminalId() == null) {
            throw new IllegalArgumentException("任务未分配终端，taskId=" + task.getTaskId());
        }
        long start = System.currentTimeMillis();

        GeoPoint origin = resolveOrigin(task, terminal);
        List<UavInspectionDevice> ordered = tspOptimizer.optimize(origin, task.getResolvedDevices());

        List<GeoPoint> deviceGeos = new ArrayList<>();
        for (UavInspectionDevice d : ordered) {
            GeoPoint p = devicePoint(d);
            if (p != null) {
                deviceGeos.add(p);
            }
        }

        List<GeoPoint> smoothed;
        List<PhotoWaypoint> photos;
        int rrtSamples = 0;
        String algorithm;

        // 输电场景：与 NLP/仿真一致的机巢-爬升-逐塔-返航折线，避免 RRT 仅 2 点导致航程过短
        if (task.getMapId() != null && patrolDeviceWaypointResolver.supportsPatrolMap(task.getMapId())) {
            PatrolRouteBuild patrol = buildPatrolCorridorRoute(ordered, deviceGeos);
            smoothed = patrol.waypoints();
            photos = patrol.photoWaypoints();
            algorithm = "PATROL_CORRIDOR+TSP";
        } else {
            List<GeoPoint> waypoints = new ArrayList<>();
            waypoints.add(origin);
            GeoPoint prev = origin;
            for (GeoPoint cur : deviceGeos) {
                List<GeoPoint> segment = rrtPlanner.plan(prev, cur, null);
                rrtSamples += segment.size();
                for (int i = 1; i < segment.size(); i++) {
                    waypoints.add(segment.get(i));
                }
                prev = cur;
            }
            List<GeoPoint> homeward = rrtPlanner.plan(prev, origin, null);
            for (int i = 1; i < homeward.size(); i++) {
                waypoints.add(homeward.get(i));
            }
            smoothed = smoothing.smooth(waypoints);
            photos = buildPhotoWaypoints(smoothed, ordered, deviceGeos);
            algorithm = "RRT*+TSP+SMOOTH";
        }

        GlobalPathPlan plan = new GlobalPathPlan();
        plan.setTakeoff(origin);
        plan.setLanding(origin);
        plan.setWaypoints(smoothed);
        plan.setPhotoWaypoints(photos);
        plan.setDeviceVisitOrder(ordered.stream().map(UavInspectionDevice::getId).toList());
        double distanceM = GeoPathUtils.totalPathLengthMeters(smoothed);
        plan.setDistanceM(distanceM);
        plan.setDurationSec((int) Math.ceil(distanceM / DispatchConstants.CRUISE_SPEED_MPS));
        plan.setBatteryPct((float) Math.min(100.0,
                (distanceM / 1000.0) * DispatchConstants.BATTERY_PCT_PER_KM));
        plan.setAlgorithm(algorithm);
        plan.setSampleCount(rrtSamples);
        plan.setElapsedMs(System.currentTimeMillis() - start);

        task.setPathPlan(plan);
        task.setStatus(DispatchTaskStatusEnum.PLANNED);
        log.info("[plan] taskId={} terminalId={} distance={}m duration={}s battery={}% wp={} elapsedMs={}",
                task.getTaskId(), task.getAssignedTerminalId(),
                Math.round(distanceM), plan.getDurationSec(),
                String.format("%.1f", plan.getBatteryPct()),
                smoothed.size(), plan.getElapsedMs());
        return plan;
    }

    /**
     * 确定起飞点优先级：终端实时位置 > 任务首点 > 兜底零点。
     */
    private GeoPoint resolveOrigin(DispatchInspectionTask task, TerminalState terminal) {
        if (task.getMapId() != null && patrolDeviceWaypointResolver.supportsPatrolMap(task.getMapId())) {
            return PatrolSceneGeometry.nestTakeoff();
        }
        if (terminal != null && terminal.getPosition() != null) {
            return terminal.getPosition();
        }
        if (!task.getDeviceWaypoints().isEmpty()) {
            return task.getDeviceWaypoints().get(0);
        }
        log.warn("[plan] taskId={} 终端 / 设备坐标均缺失，使用机巢兜底", task.getTaskId());
        return PatrolSceneGeometry.nestTakeoff();
    }

    /** 设备 → GeoPoint；输电杆塔无坐标时按场景几何解析 */
    private GeoPoint devicePoint(UavInspectionDevice d) {
        if (d == null) {
            return null;
        }
        if (d.getLongitude() != null && d.getLatitude() != null) {
            return new GeoPoint(d.getLongitude(), d.getLatitude(),
                    d.getHeight() == null ? 0.0 : d.getHeight());
        }
        return patrolDeviceWaypointResolver.parseTowerIndex(d.getDeviceName())
                .map(PatrolSceneGeometry::towerPhotoPoint)
                .orElse(null);
    }

    /**
     * 将每个设备点绑定到平滑后航点序列中最近的索引。
     */
    private List<PhotoWaypoint> buildPhotoWaypoints(List<GeoPoint> waypoints,
                                                    List<UavInspectionDevice> ordered,
                                                    List<GeoPoint> deviceGeos) {
        List<PhotoWaypoint> photos = new ArrayList<>();
        for (int i = 0; i < ordered.size() && i < deviceGeos.size(); i++) {
            UavInspectionDevice d = ordered.get(i);
            GeoPoint p = deviceGeos.get(i);
            int idx = nearestWaypointIndex(waypoints, p);

            PhotoWaypoint photo = new PhotoWaypoint();
            photo.setLongitude(p.getLongitude());
            photo.setLatitude(p.getLatitude());
            photo.setHeight(p.getHeight());
            photo.setWaypointIndex(idx);
            photo.setDeviceIds(List.of(d.getId()));
            photos.add(photo);
        }
        return photos;
    }

    /**
     * 输电走廊标准航线：机巢起飞 → 爬升 → 各杆塔拍照点 → 返航。
     */
    private PatrolRouteBuild buildPatrolCorridorRoute(List<UavInspectionDevice> ordered,
                                                      List<GeoPoint> deviceGeos) {
        GeoPoint takeoff = PatrolSceneGeometry.nestTakeoff();
        GeoPoint transit = PatrolSceneGeometry.nestTransit();
        GeoPoint landing = PatrolSceneGeometry.nestTakeoff();

        List<GeoPoint> waypoints = new ArrayList<>();
        waypoints.add(takeoff);
        waypoints.add(transit);

        List<PhotoWaypoint> photos = new ArrayList<>();
        for (int i = 0; i < ordered.size() && i < deviceGeos.size(); i++) {
            GeoPoint p = deviceGeos.get(i);
            waypoints.add(p);
            PhotoWaypoint photo = new PhotoWaypoint();
            photo.setLongitude(p.getLongitude());
            photo.setLatitude(p.getLatitude());
            photo.setHeight(p.getHeight());
            photo.setWaypointIndex(waypoints.size() - 2);
            photo.setDeviceIds(List.of(ordered.get(i).getId()));
            photos.add(photo);
        }
        waypoints.add(landing);
        return new PatrolRouteBuild(waypoints, photos);
    }

    private record PatrolRouteBuild(List<GeoPoint> waypoints, List<PhotoWaypoint> photoWaypoints) {
    }

    /** 找到 waypoints 中距离 target 最近点的下标 */
    private int nearestWaypointIndex(List<GeoPoint> waypoints, GeoPoint target) {
        int idx = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < waypoints.size(); i++) {
            double d = GeoPathUtils.segmentLengthMeters(waypoints.get(i), target);
            if (d < best) {
                best = d;
                idx = i;
            }
        }
        return idx;
    }
}
