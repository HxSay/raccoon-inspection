package com.raccoon.cloud.drone.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskStatusEnum;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.DispatchWorkOrder;
import com.raccoon.cloud.drone.dispatch.model.GlobalPathPlan;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.PhotoWaypoint;
import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import com.raccoon.cloud.drone.entity.UavRoutePlan;
import com.raccoon.cloud.drone.enums.InspectionRobotTypeEnum;
import com.raccoon.cloud.drone.service.UavRoutePlanService;
import com.raccoon.cloud.drone.util.PhotoWaypointUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 作业清单生成与下发服务。
 * <p>职责：
 * <ol>
 *   <li>根据终端类型差异化转换路径规划为下发 JSON（UAV 高速航迹 / 机器狗低速轨迹）</li>
 *   <li>持久化到 {@code uav_route_plan}，便于追溯与对账</li>
 *   <li>向边缘执行 Agent 下发（默认本地存档；外部 Feign 可在此扩展）</li>
 * </ol>
 *
 * @author raccoon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDispatchService {

    private final UavRoutePlanService routePlanService;
    private final ObjectMapper objectMapper;

    /**
     * 生成并下发作业清单。
     *
     * @param task      任务（必须已 PLANNED）
     * @param terminal  目标终端
     * @param dispatch  是否真实下发到边缘 Agent
     * @return 作业清单
     */
    @Transactional(rollbackFor = Exception.class)
    public DispatchWorkOrder generateAndDispatch(DispatchInspectionTask task,
                                                 TerminalState terminal,
                                                 boolean dispatch) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        if (task.getPathPlan() == null) {
            throw new IllegalArgumentException("任务未完成路径规划，taskId=" + task.getTaskId());
        }
        if (task.getAssignedTerminalId() == null) {
            throw new IllegalArgumentException("任务未分配终端，taskId=" + task.getTaskId());
        }

        UavRoutePlan saved = persistRoutePlan(task);
        UavRouteDispatchPayload payload = buildPayload(task, terminal, saved.getId());

        DispatchWorkOrder order = new DispatchWorkOrder();
        order.setTaskId(task.getTaskId());
        order.setPlanId(saved.getId());
        order.setTerminalId(task.getAssignedTerminalId());
        order.setTerminalType(terminal != null ? terminal.getTerminalType()
                : InspectionRobotTypeEnum.UAV.getCode());
        order.setTaskTypeCode(task.getTaskType() != null ? task.getTaskType().getCode() : null);
        order.setPriority(task.getPriority());
        order.setPayload(payload);
        order.setDispatchTime(LocalDateTime.now());

        if (dispatch) {
            try {
                dispatchToEdge(order);
                order.setDispatchResult("SUCCESS");
                task.setStatus(DispatchTaskStatusEnum.DISPATCHED);
            } catch (Exception e) {
                log.error("[dispatch] taskId={} 下发失败", task.getTaskId(), e);
                order.setDispatchResult("FAILED");
                order.setErrorMessage(e.getMessage());
                task.setStatus(DispatchTaskStatusEnum.FAILED);
            }
        } else {
            order.setDispatchResult("PENDING");
            task.setStatus(DispatchTaskStatusEnum.PLANNED);
        }
        return order;
    }

    /**
     * 落库 uav_route_plan：路径点、拍照点、设备访问顺序均序列化为 JSON 字符串。
     */
    private UavRoutePlan persistRoutePlan(DispatchInspectionTask task) {
        GlobalPathPlan plan = task.getPathPlan();
        UavRoutePlan row = new UavRoutePlan();
        row.setTaskId(parseLong(task.getTaskId()));
        row.setMapId(task.getMapId());
        row.setUavId(task.getAssignedTerminalId());
        row.setStartPoint(serializePoint(plan.getTakeoff()));
        row.setEndPoint(serializePoint(plan.getLanding()));
        row.setTotalDistance(plan.getDistanceM());
        row.setEstimatedTime(plan.getDurationSec());
        row.setEstimatedBattery(plan.getBatteryPct());
        row.setAlgorithm(truncate(plan.getAlgorithm(), 20));
        try {
            row.setPathPoints(objectMapper.writeValueAsString(plan.getWaypoints()));
            row.setPhotoPoints(objectMapper.writeValueAsString(plan.getPhotoWaypoints()));
            row.setVisitOrder(objectMapper.writeValueAsString(plan.getDeviceVisitOrder()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("路径规划 JSON 序列化失败", e);
        }
        routePlanService.save(row);
        log.debug("[dispatch] uav_route_plan saved id={} taskId={}", row.getId(), task.getTaskId());
        return row;
    }

    /**
     * 构建下发 JSON。
     * <p>差异化处理：机器狗/地面机器人的 algorithm 后缀为 "-GROUND"，并夹断高度上限。
     */
    private UavRouteDispatchPayload buildPayload(DispatchInspectionTask task,
                                                 TerminalState terminal,
                                                 Long planId) {
        GlobalPathPlan plan = task.getPathPlan();
        UavRouteDispatchPayload payload = new UavRouteDispatchPayload();
        payload.setPlanId(planId);
        payload.setTaskId(parseLong(task.getTaskId()));
        payload.setMapId(task.getMapId());
        payload.setUavId(task.getAssignedTerminalId());

        String algorithm = plan.getAlgorithm();
        if (terminal != null && isGroundType(terminal.getTerminalType())) {
            algorithm = (algorithm == null ? "RRT*" : algorithm) + "-GROUND";
        }
        payload.setAlgorithm(truncate(algorithm, 32));
        payload.setTakeoff(plan.getTakeoff());
        payload.setLanding(plan.getLanding());

        List<GeoPoint> waypoints = plan.getWaypoints();
        if (terminal != null && isGroundType(terminal.getTerminalType())) {
            waypoints = clampHeight(waypoints, 2.0);
        }
        payload.setWaypoints(waypoints);
        payload.setPhotoWaypoints(plan.getPhotoWaypoints());
        payload.setDeviceVisitOrder(PhotoWaypointUtils.hasBoundDevices(plan.getPhotoWaypoints())
                ? PhotoWaypointUtils.flattenDeviceVisitOrder(plan.getPhotoWaypoints())
                : plan.getDeviceVisitOrder());

        UavRouteDispatchPayload.Estimated est = new UavRouteDispatchPayload.Estimated();
        est.setDistanceM(plan.getDistanceM());
        est.setDurationSec(plan.getDurationSec());
        est.setBatteryPct(plan.getBatteryPct());
        payload.setEstimated(est);
        return payload;
    }

    /**
     * 实际下发到边缘 Agent 的 hook。
     * <p>当前为本地存档（仅日志记录）；接入 Feign / MQ 时在此扩展即可。
     */
    private void dispatchToEdge(DispatchWorkOrder order) {
        log.info("[dispatch] -> edge terminalId={} taskId={} planId={} waypoints={}",
                order.getTerminalId(), order.getTaskId(), order.getPlanId(),
                order.getPayload().getWaypoints() == null ? 0 : order.getPayload().getWaypoints().size());
    }

    private boolean isGroundType(String type) {
        if (type == null) {
            return false;
        }
        return type.equalsIgnoreCase(InspectionRobotTypeEnum.ROBOT_DOG.getCode())
                || type.equalsIgnoreCase(InspectionRobotTypeEnum.GROUND_ROBOT.getCode())
                || type.equalsIgnoreCase(InspectionRobotTypeEnum.WHEELED.getCode());
    }

    private List<GeoPoint> clampHeight(List<GeoPoint> points, double maxH) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        return points.stream()
                .map(p -> new GeoPoint(p.getLongitude(), p.getLatitude(),
                        p.getHeight() == null ? 0.0 : Math.min(p.getHeight(), maxH)))
                .toList();
    }

    private String serializePoint(GeoPoint p) {
        if (p == null) {
            return null;
        }
        return p.getLongitude() + "," + p.getLatitude() + ","
                + (p.getHeight() == null ? 0.0 : p.getHeight());
    }

    private Long parseLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
