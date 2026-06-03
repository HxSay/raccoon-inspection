package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.system.cmms.constants.InspectionWorkOrderStatus;
import com.raccoon.cloud.system.cmms.entity.DeviceInfo;
import com.raccoon.cloud.system.cmms.entity.InspectionPlan;
import com.raccoon.cloud.system.cmms.entity.InspectionPoint;
import com.raccoon.cloud.system.cmms.entity.InspectionTask;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrder;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrderDetail;
import com.raccoon.cloud.system.cmms.mapper.DeviceInfoMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionPlanMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionPointMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionTaskMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderDetailMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderMapper;
import com.raccoon.cloud.system.mapper.UserMapper;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.cmms.service.AgentPathPlanHelper.PhotoAnchor;
import com.raccoon.common.dto.planning.PlanningWorkOrderSubmitRequest;
import com.raccoon.common.dto.planning.PlanningWorkOrderSubmitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务规划 Agent 巡检工单自动生成：计划/任务/工单绑定，标准四步，检测项关联。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderGenerateService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InspectionPlanMapper planMapper;
    private final InspectionTaskMapper taskMapper;
    private final InspectionWorkOrderMapper orderMapper;
    private final InspectionWorkOrderDetailMapper detailMapper;
    private final DeviceInfoMapper deviceInfoMapper;
    private final InspectionPointMapper pointMapper;
    private final InspectionWorkOrderService inspectionWorkOrderService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Value("${raccoon.planning.default-inspector-id:1}")
    private long defaultInspectorId;

    @Value("${raccoon.planning.audit-timeout-hours:24}")
    private int auditTimeoutHours;

    @Transactional(rollbackFor = Exception.class)
    public PlanningWorkOrderSubmitResponse submitFromPlanning(PlanningWorkOrderSubmitRequest req) {
        validateSubmit(req);
        List<DeviceInfo> devices = resolveDevices(req.getDeviceNames());
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("未解析到有效巡检设备");
        }

        LocalDateTime now = LocalDateTime.now();
        String area = StringUtils.hasText(req.getAreaName()) ? req.getAreaName().trim() : "Agent规划区域";
        Long inspectorId = resolveInspectorId(req.getInspectorId());
        String inspectorName = resolveInspectorName(inspectorId, req.getInspectorName());

        InspectionPlan plan = new InspectionPlan();
        plan.setPlanName("Agent规划-" + area + "-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")));
        plan.setDeviceIds(toDeviceIdsJson(devices));
        plan.setCycleType(0);
        plan.setCycleValue(1);
        plan.setExecUserId(inspectorId);
        plan.setStartTime(now);
        plan.setEndTime(now.plusDays(7));
        plan.setStatus(1);
        planMapper.insert(plan);

        DeviceInfo primary = devices.get(0);
        InspectionTask task = new InspectionTask();
        task.setTaskCode("AT-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        task.setPlanId(plan.getId());
        task.setDeviceId(primary.getId());
        task.setTaskName(area + " 巡检");
        task.setExecUserId(plan.getExecUserId());
        task.setPlanExecuteTime(now.plusHours(1));
        task.setStatus(0);
        task.setRemark(trim(req.getRemark()));
        taskMapper.insert(task);

        InspectionWorkOrder order = new InspectionWorkOrder();
        order.setOrderNo(genAgentOrderNo());
        order.setArea(area);
        order.setShiftType(1);
        order.setInspectorId(inspectorId);
        order.setInspectorName(inspectorName);
        order.setPlanStartTime(now);
        order.setPlanEndTime(now.plusDays(1));
        order.setStatus(InspectionWorkOrderStatus.PENDING_AUDIT);
        order.setCreateBy(1L);
        order.setCreateByName("任务规划Agent");
        order.setPlanId(plan.getId());
        order.setTaskId(task.getId());
        order.setDispatchTaskId(trim(req.getDispatchTaskId()));
        order.setPriorityCode(normalizePriority(req.getPriorityCode()));
        order.setTerminalId(req.getAssignedTerminalId());
        order.setTerminalName(trim(req.getAssignedTerminalName()));
        order.setAssignReason(trim(req.getAssignReason()));
        order.setPathPlanJson(req.getPathPlanJson());
        order.setRemark(buildOrderRemark(req));
        order.setAuditDeadline(now.plusHours(auditTimeoutHours));
        orderMapper.insert(order);

        List<PhotoAnchor> photoAnchors = AgentPathPlanHelper.parsePhotoAnchors(req.getPathPlanJson(), objectMapper);
        int stepNo = 1;
        int devIdx = 0;
        for (DeviceInfo dev : devices) {
            inspectionWorkOrderService.ensureDeviceRow(dev.getId());
            ensureDefaultInspectionPoints(dev);
            List<InspectionPoint> points = pointMapper.selectList(
                    new QueryWrapper<InspectionPoint>()
                            .eq("device_id", dev.getId())
                            .orderByAsc("sort", "id"));
            PhotoAnchor anchor = devIdx < photoAnchors.size() ? photoAnchors.get(devIdx) : null;
            stepNo = appendStandardSteps(order.getId(), stepNo, area, dev, points, anchor);
            devIdx++;
        }

        task.setWorkOrderId(order.getId());
        taskMapper.updateById(task);

        PlanningWorkOrderSubmitResponse resp = new PlanningWorkOrderSubmitResponse();
        resp.setPlanId(plan.getId());
        resp.setTaskId(task.getId());
        resp.setWorkOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());
        resp.setAuditDeadline(order.getAuditDeadline());
        resp.setMessage("巡检工单已生成，待移动端审核");
        log.info("[WorkOrderGenerate] orderNo={} dispatchTaskId={} devices={}",
                order.getOrderNo(), order.getDispatchTaskId(), devices.size());
        return resp;
    }

    @Transactional(rollbackFor = Exception.class)
    public PlanningWorkOrderSubmitResponse resubmitAfterReplan(Long workOrderId, PlanningWorkOrderSubmitRequest req) {
        InspectionWorkOrder order = orderMapper.selectById(workOrderId);
        if (order == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (order.getStatus() != InspectionWorkOrderStatus.AUDIT_REJECTED) {
            throw new IllegalArgumentException("仅审核驳回工单可重新提交");
        }
        detailMapper.delete(new QueryWrapper<InspectionWorkOrderDetail>().eq("order_id", workOrderId));

        List<DeviceInfo> devices = resolveDevices(req.getDeviceNames());
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("未解析到有效巡检设备");
        }
        String area = StringUtils.hasText(req.getAreaName()) ? req.getAreaName().trim() : order.getArea();
        List<PhotoAnchor> photoAnchors = AgentPathPlanHelper.parsePhotoAnchors(req.getPathPlanJson(), objectMapper);
        int stepNo = 1;
        int devIdx = 0;
        for (DeviceInfo dev : devices) {
            inspectionWorkOrderService.ensureDeviceRow(dev.getId());
            ensureDefaultInspectionPoints(dev);
            List<InspectionPoint> points = pointMapper.selectList(
                    new QueryWrapper<InspectionPoint>()
                            .eq("device_id", dev.getId())
                            .orderByAsc("sort", "id"));
            PhotoAnchor anchor = devIdx < photoAnchors.size() ? photoAnchors.get(devIdx) : null;
            stepNo = appendStandardSteps(workOrderId, stepNo, area, dev, points, anchor);
            devIdx++;
        }

        order.setDispatchTaskId(trim(req.getDispatchTaskId()));
        order.setPriorityCode(normalizePriority(req.getPriorityCode()));
        order.setTerminalId(req.getAssignedTerminalId());
        order.setTerminalName(trim(req.getAssignedTerminalName()));
        order.setAssignReason(trim(req.getAssignReason()));
        order.setPathPlanJson(req.getPathPlanJson());
        order.setRejectReason(null);
        order.setStatus(InspectionWorkOrderStatus.PENDING_AUDIT);
        order.setAuditDeadline(LocalDateTime.now().plusHours(auditTimeoutHours));
        order.setRemark(buildOrderRemark(req));
        orderMapper.updateById(order);

        PlanningWorkOrderSubmitResponse resp = new PlanningWorkOrderSubmitResponse();
        resp.setPlanId(order.getPlanId());
        resp.setTaskId(order.getTaskId());
        resp.setWorkOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());
        resp.setAuditDeadline(order.getAuditDeadline());
        resp.setMessage("驳回后已重新规划，待审核");
        return resp;
    }

    public void storePlanningPayload(Long workOrderId, String payloadJson) {
        if (workOrderId == null) {
            return;
        }
        InspectionWorkOrder order = orderMapper.selectById(workOrderId);
        if (order == null) {
            return;
        }
        order.setPlanningPayloadJson(payloadJson);
        orderMapper.updateById(order);
    }

    /**
     * 工单编号：XJ-yyyyMMdd-xxxx（当日 4 位自增流水号）。
     */
    public String genAgentOrderNo() {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "XJ-" + day + "-";
        InspectionWorkOrder last = orderMapper.selectOne(
                new QueryWrapper<InspectionWorkOrder>()
                        .likeRight("order_no", prefix)
                        .orderByDesc("order_no")
                        .last("LIMIT 1"));
        int next = 1;
        if (last != null && last.getOrderNo() != null && last.getOrderNo().startsWith(prefix)) {
            String tail = last.getOrderNo().substring(prefix.length());
            try {
                next = Integer.parseInt(tail) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
        }
        if (next > 9999) {
            throw new IllegalStateException("当日工单流水号已用尽");
        }
        return prefix + String.format("%04d", next);
    }

    private void ensureDefaultInspectionPoints(DeviceInfo dev) {
        long cnt = pointMapper.selectCount(
                new QueryWrapper<InspectionPoint>().eq("device_id", dev.getId()));
        if (cnt > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        insertPoint(dev.getId(), "可见光外观", 1, null, null, null, 1, now);
        insertPoint(dev.getId(), "红外热成像温度", 2, new BigDecimal("20"), new BigDecimal("85"), "℃", 2, now);
        insertPoint(dev.getId(), "振动幅值", 3, new BigDecimal("0"), new BigDecimal("5"), "mm/s", 3, now);
        log.info("[WorkOrderGenerate] 已为设备 {} 初始化默认检测项", dev.getDeviceName());
    }

    private void insertPoint(Long deviceId, String name, int type, BigDecimal min, BigDecimal max,
                             String unit, int sort, LocalDateTime now) {
        InspectionPoint p = new InspectionPoint();
        p.setDeviceId(deviceId);
        p.setPointName(name);
        p.setPointType(type);
        p.setMinThreshold(min);
        p.setMaxThreshold(max);
        p.setUnit(unit);
        p.setSort(sort);
        p.setCreateTime(now);
        p.setUpdateTime(now);
        pointMapper.insert(p);
    }

    private int appendStandardSteps(Long orderId, int stepNo, String area, DeviceInfo dev,
                                    List<InspectionPoint> points, PhotoAnchor anchor) {
        String dname = dev.getDeviceName();
        Long devId = dev.getId();
        String loc = StringUtils.hasText(dev.getLocation()) ? dev.getLocation() : area;
        String wpRemark = anchor != null ? "waypointIndex=" + anchor.getWaypointIndex() : null;

        String pathDesc = "按路径前往 " + dname;
        if (anchor != null && anchor.getLatitude() != null) {
            pathDesc += String.format("（航点#%d, %.6f, %.6f）", anchor.getWaypointIndex(),
                    anchor.getLongitude(), anchor.getLatitude());
        }
        stepNo = insertDetail(orderId, stepNo, "path", area, pathDesc, devId, dname, null, null, null, null, wpRemark);

        stepNo = insertDetail(orderId, stepNo, "stop", loc, "停靠 " + dname + " 并确认设备", devId, dname,
                "设备到位确认", null, null, null, wpRemark);

        if (points == null || points.isEmpty()) {
            stepNo = insertDetail(orderId, stepNo, "collect", loc, "可见光/多模态巡检采集", devId, dname,
                    "可见光外观", null, null, null, wpRemark);
        } else {
            for (InspectionPoint p : points) {
                String desc = "采集「" + (p.getPointName() != null ? p.getPointName() : "测点") + "」";
                if (StringUtils.hasText(p.getUnit()) && p.getMinThreshold() != null && p.getMaxThreshold() != null) {
                    desc += String.format("（标准 %s~%s %s）", p.getMinThreshold(), p.getMaxThreshold(), p.getUnit());
                }
                stepNo = insertDetail(orderId, stepNo, "collect", loc, desc, devId, dname,
                        p.getPointName(), p.getMinThreshold(), p.getMaxThreshold(), p.getUnit(), wpRemark);
            }
        }
        stepNo = insertDetail(orderId, stepNo, "report", area, dname + " 无人机巡检结果上报", devId, dname,
                "巡检结果汇总", null, null, null, wpRemark);
        return stepNo;
    }

    private int insertDetail(Long orderId, int stepNo, String type, String target, String desc,
                             Long deviceId, String deviceName, String checkItem,
                             BigDecimal min, BigDecimal max, String unit, String remark) {
        InspectionWorkOrderDetail d = new InspectionWorkOrderDetail();
        d.setOrderId(orderId);
        d.setStepOrder(stepNo);
        d.setStepType(type);
        d.setTarget(target);
        d.setDescription(desc);
        d.setDeviceId(deviceId);
        d.setDeviceName(deviceName);
        d.setCheckItem(checkItem);
        d.setStandardMin(min);
        d.setStandardMax(max);
        d.setUnit(unit);
        d.setRemark(remark);
        d.setIsException(0);
        detailMapper.insert(d);
        return stepNo + 1;
    }

    private List<DeviceInfo> resolveDevices(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<DeviceInfo> out = new ArrayList<>();
        for (String name : names) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String n = name.trim();
            DeviceInfo found = deviceInfoMapper.selectOne(
                    new QueryWrapper<DeviceInfo>().eq("device_name", n).last("LIMIT 1"));
            if (found == null) {
                found = deviceInfoMapper.selectOne(
                        new QueryWrapper<DeviceInfo>().like("device_name", n).last("LIMIT 1"));
            }
            final DeviceInfo device = found;
            if (device != null && out.stream().noneMatch(x -> x.getId().equals(device.getId()))) {
                out.add(device);
            }
        }
        return out;
    }

    private String toDeviceIdsJson(List<DeviceInfo> devices) {
        try {
            List<Long> ids = devices.stream().map(DeviceInfo::getId).collect(Collectors.toList());
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private void validateSubmit(PlanningWorkOrderSubmitRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("提交体不能为空");
        }
        if (!StringUtils.hasText(req.getDispatchTaskId())) {
            throw new IllegalArgumentException("缺少调度任务ID");
        }
    }

    private String buildOrderRemark(PlanningWorkOrderSubmitRequest req) {
        StringBuilder sb = new StringBuilder("任务规划Agent自动生成");
        if (StringUtils.hasText(req.getUserInput())) {
            sb.append("；输入：").append(req.getUserInput().trim());
        }
        if (StringUtils.hasText(req.getTaskTypeCode())) {
            sb.append("；类型：").append(req.getTaskTypeCode());
        }
        return sb.toString();
    }

    private String normalizePriority(String code) {
        if (!StringUtils.hasText(code)) {
            return "NORMAL";
        }
        return code.trim().toUpperCase();
    }

    private String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private Long resolveInspectorId(Long fromRequest) {
        return fromRequest != null ? fromRequest : defaultInspectorId;
    }

    private String resolveInspectorName(Long inspectorId, String fromRequest) {
        if (StringUtils.hasText(fromRequest)) {
            return fromRequest.trim();
        }
        User u = userMapper.selectById(inspectorId);
        if (u == null) {
            return "系统默认审核人";
        }
        if (StringUtils.hasText(u.getNickname())) {
            return u.getNickname();
        }
        return u.getUsername();
    }
}
