package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.cmms.constants.InspectionWorkOrderStatus;
import com.raccoon.cloud.system.cmms.dto.*;
import com.raccoon.cloud.system.cmms.entity.DeviceInfo;
import com.raccoon.cloud.system.cmms.entity.GridDevice;
import com.raccoon.cloud.system.cmms.entity.InspectionPlan;
import com.raccoon.cloud.system.cmms.entity.InspectionTask;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrder;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrderDetail;
import com.raccoon.cloud.system.cmms.mapper.DeviceInfoMapper;
import com.raccoon.cloud.system.cmms.mapper.GridDeviceMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionPlanMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionTaskMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderDetailMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderMapper;
import com.raccoon.cloud.system.cmms.vo.InspectionWorkOrderFullVO;
import com.raccoon.cloud.system.cmms.vo.InspectionWorkOrderStepVO;
import com.raccoon.cloud.system.mapper.UserMapper;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 巡检工单：创建、按计划派发、下发、取消、分页、执行、采集、扫码关联设备。
 */
@Service
public class InspectionWorkOrderService {

    private static final String METHOD_TAG = "[检测方式]";

    private final InspectionWorkOrderMapper orderMapper;
    private final InspectionWorkOrderDetailMapper detailMapper;
    private final GridDeviceMapper gridDeviceMapper;
    private final DeviceInfoMapper deviceInfoMapper;
    private final InspectionTaskMapper taskMapper;
    private final InspectionPlanMapper planMapper;
    private final UserMapper userMapper;
    private final UserService userService;

    public InspectionWorkOrderService(
            InspectionWorkOrderMapper orderMapper,
            InspectionWorkOrderDetailMapper detailMapper,
            GridDeviceMapper gridDeviceMapper,
            DeviceInfoMapper deviceInfoMapper,
            InspectionTaskMapper taskMapper,
            InspectionPlanMapper planMapper,
            UserMapper userMapper,
            UserService userService) {
        this.orderMapper = orderMapper;
        this.detailMapper = detailMapper;
        this.gridDeviceMapper = gridDeviceMapper;
        this.deviceInfoMapper = deviceInfoMapper;
        this.taskMapper = taskMapper;
        this.planMapper = planMapper;
        this.userMapper = userMapper;
        this.userService = userService;
    }

    private User safeCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !StringUtils.hasText(auth.getName()) || "anonymousUser".equals(auth.getName())) {
                return null;
            }
            return userService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 device_info 中设备同步到 device 表（保持 id），便于外键与扫码。
     */
    public void ensureDeviceRow(Long deviceInfoId) {
        if (deviceInfoId == null) {
            return;
        }
        if (gridDeviceMapper.selectById(deviceInfoId) != null) {
            return;
        }
        DeviceInfo src = deviceInfoMapper.selectById(deviceInfoId);
        if (src == null) {
            throw new IllegalArgumentException("设备不存在: " + deviceInfoId);
        }
        GridDevice g = new GridDevice();
        BeanUtils.copyProperties(src, g);
        gridDeviceMapper.insert(g);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(InspectionWorkOrderCreateRequest req) {
        validateSteps(req.getSteps());
        validateTaskBinding(req);
        User cu = safeCurrentUser();
        Long createBy = cu != null ? cu.getId() : 1L;
        String createByName = cu != null && StringUtils.hasText(cu.getNickname()) ? cu.getNickname() : (cu != null ? cu.getUsername() : "system");

        for (InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft s : req.getSteps()) {
            if (s.getDeviceId() != null) {
                ensureDeviceRow(s.getDeviceId());
            }
        }

        InspectionWorkOrder order = new InspectionWorkOrder();
        order.setOrderNo(genOrderNo());
        order.setArea(req.getArea().trim());
        order.setShiftType(req.getShiftType());
        order.setInspectorId(req.getInspectorId());
        order.setInspectorName(req.getInspectorName());
        order.setPlanStartTime(parseDt(req.getPlanStartTime()));
        order.setPlanEndTime(parseDt(req.getPlanEndTime()));
        order.setStatus(InspectionWorkOrderStatus.PENDING_ISSUE);
        order.setCreateBy(createBy);
        order.setCreateByName(createByName);
        order.setPlanId(req.getPlanId());
        order.setTaskId(req.getTaskId());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        for (InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft s : req.getSteps()) {
            InspectionWorkOrderDetail d = new InspectionWorkOrderDetail();
            d.setOrderId(order.getId());
            d.setStepOrder(s.getStepOrder());
            d.setStepType(normalizeType(s.getType()));
            d.setTarget(trimToNull(s.getTarget()));
            d.setDescription(mergeDescriptionWithMethod(s.getDescription(), s.getDetectionMethod()));
            d.setDeviceId(s.getDeviceId());
            d.setDeviceName(trimToNull(s.getDeviceName()));
            d.setCheckItem(trimToNull(s.getCheckItem()));
            d.setStandardMin(s.getStandardMin());
            d.setStandardMax(s.getStandardMax());
            d.setUnit(trimToNull(s.getUnit()));
            d.setIsException(0);
            detailMapper.insert(d);
        }
        if (req.getTaskId() != null) {
            bindTaskToWorkOrder(req.getTaskId(), order.getId());
        }
        return order.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void issue(InspectionWorkOrderIdRequest req) {
        InspectionWorkOrder o = orderMapper.selectById(req.getId());
        if (o == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (o.getStatus() != InspectionWorkOrderStatus.PENDING_ISSUE) {
            throw new IllegalArgumentException("仅待下发状态可下发");
        }
        o.setStatus(InspectionWorkOrderStatus.PENDING_EXEC);
        User u = safeCurrentUser();
        if (u != null) {
            o.setUpdateBy(u.getId());
        }
        orderMapper.updateById(o);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(InspectionWorkOrderIdRequest req) {
        InspectionWorkOrder o = orderMapper.selectById(req.getId());
        if (o == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (o.getStatus() != InspectionWorkOrderStatus.PENDING_ISSUE
                && o.getStatus() != InspectionWorkOrderStatus.PENDING_EXEC) {
            throw new IllegalArgumentException("仅待下发或待执行状态可取消");
        }
        o.setStatus(InspectionWorkOrderStatus.CANCELLED);
        User u = safeCurrentUser();
        if (u != null) {
            o.setUpdateBy(u.getId());
        }
        Long oid = o.getId();
        Long tid = o.getTaskId();
        orderMapper.updateById(o);
        if (tid != null) {
            InspectionTask t = taskMapper.selectById(tid);
            if (t != null && Objects.equals(oid, t.getWorkOrderId())) {
                t.setWorkOrderId(null);
                taskMapper.updateById(t);
            }
        }
    }

    /**
     * 按启用计划，向「待执行且未绑定工单」的巡检任务各生成一张工单并自动下发到任务执行人。
     */
    @Transactional(rollbackFor = Exception.class)
    public int dispatchFromPlan(Long planId) {
        InspectionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        if (plan.getStatus() == null || plan.getStatus() != 1) {
            throw new IllegalArgumentException("计划未启用，无法派发工单");
        }
        List<InspectionTask> tasks = taskMapper.selectList(
                new QueryWrapper<InspectionTask>()
                        .eq("plan_id", planId)
                        .eq("status", 0)
                        .isNull("work_order_id"));
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("暂无可派发的待执行任务，请先在计划中「生成任务」");
        }
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int n = 0;
        for (InspectionTask task : tasks) {
            DeviceInfo dev = deviceInfoMapper.selectById(task.getDeviceId());
            InspectionWorkOrderCreateRequest req = buildDispatchWorkOrderRequest(plan, task, dev, dtf);
            Long orderId = create(req);
            issue(new InspectionWorkOrderIdRequest(orderId));
            n++;
        }
        return n;
    }

    public IPage<InspectionWorkOrder> page(InspectionWorkOrderPageRequest req) {
        QueryWrapper<InspectionWorkOrder> w = new QueryWrapper<>();
        if (StringUtils.hasText(req.getArea())) {
            w.like("area", req.getArea().trim());
        }
        if (req.getShiftType() != null) {
            w.eq("shift_type", req.getShiftType());
        }
        if (req.getStatus() != null) {
            w.eq("status", req.getStatus());
        }
        if (req.getPlanId() != null) {
            w.eq("plan_id", req.getPlanId());
        }
        if (req.getTaskId() != null) {
            w.eq("task_id", req.getTaskId());
        }
        if (StringUtils.hasText(req.getPlanStartFrom())) {
            w.ge("plan_start_time", parseDt(req.getPlanStartFrom()));
        }
        if (StringUtils.hasText(req.getPlanStartTo())) {
            w.le("plan_start_time", parseDt(req.getPlanStartTo()));
        }
        w.orderByDesc("id");
        Page<InspectionWorkOrder> p = new Page<>(req.getPage(), req.getSize());
        return orderMapper.selectPage(p, w);
    }

    public IPage<InspectionWorkOrder> myPage(InspectionWorkOrderMyPageRequest req) {
        Long inspectorId = req.getInspectorId();
        if (inspectorId == null) {
            User u = safeCurrentUser();
            if (u == null) {
                throw new IllegalArgumentException("未登录且未传 inspectorId");
            }
            inspectorId = u.getId();
        }
        int status;
        switch (req.getTab()) {
            case "pending":
                status = InspectionWorkOrderStatus.PENDING_EXEC;
                break;
            case "running":
                status = InspectionWorkOrderStatus.RUNNING;
                break;
            case "done":
                status = InspectionWorkOrderStatus.FINISHED;
                break;
            default:
                throw new IllegalArgumentException("tab 仅支持 pending/running/done");
        }
        QueryWrapper<InspectionWorkOrder> w = new QueryWrapper<>();
        w.eq("inspector_id", inspectorId);
        w.eq("status", status);
        w.orderByDesc("id");
        Page<InspectionWorkOrder> p = new Page<>(req.getPage(), req.getSize());
        return orderMapper.selectPage(p, w);
    }

    public InspectionWorkOrderFullVO detailFull(Long orderId) {
        InspectionWorkOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        List<InspectionWorkOrderDetail> details = detailMapper.selectList(
                new QueryWrapper<InspectionWorkOrderDetail>().eq("order_id", orderId).orderByAsc("step_order"));
        Map<Long, GridDevice> deviceMap = loadDeviceMap(details);

        InspectionWorkOrderFullVO vo = new InspectionWorkOrderFullVO();
        BeanUtils.copyProperties(order, vo);
        vo.setSteps(details.stream().map(d -> toStepVO(d, deviceMap)).collect(Collectors.toList()));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeStep(InspectionStepExecuteRequest req) {
        InspectionWorkOrderDetail d = detailMapper.selectById(req.getDetailId());
        if (d == null) {
            throw new IllegalArgumentException("步骤不存在");
        }
        InspectionWorkOrder order = orderMapper.selectById(d.getOrderId());
        assertExecutableOrder(order);
        assertCurrentStep(order.getId(), d);
        String st = d.getStepType().toLowerCase();
        if ("collect".equals(st)) {
            throw new IllegalArgumentException("采集步骤请使用采集上报接口");
        }
        if ("stop".equals(st) && d.getDeviceId() == null) {
            throw new IllegalArgumentException("停靠步骤请先扫码绑定设备");
        }
        d.setCollectTime(LocalDateTime.now());
        if (StringUtils.hasText(req.getRemark())) {
            d.setRemark(req.getRemark().trim());
        }
        detailMapper.updateById(d);
        refreshOrderProgress(order.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void collectStep(InspectionStepCollectRequest req) {
        InspectionWorkOrderDetail d = detailMapper.selectById(req.getDetailId());
        if (d == null) {
            throw new IllegalArgumentException("步骤不存在");
        }
        if (!"collect".equalsIgnoreCase(d.getStepType())) {
            throw new IllegalArgumentException("仅采集步骤可上报实测值");
        }
        InspectionWorkOrder order = orderMapper.selectById(d.getOrderId());
        assertExecutableOrder(order);
        assertCurrentStep(order.getId(), d);

        BigDecimal val = parseNumericActual(req.getActualValue());
        int ex = computeException(d.getStandardMin(), d.getStandardMax(), val);

        d.setActualValue(req.getActualValue().trim());
        d.setPhotoUrl(trimToNull(req.getPhotoUrl()));
        if (StringUtils.hasText(req.getRemark())) {
            d.setRemark(req.getRemark().trim());
        }
        d.setIsException(ex);
        d.setCollectTime(LocalDateTime.now());
        detailMapper.updateById(d);
        refreshOrderProgress(order.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void scanDevice(InspectionScanDeviceRequest req) {
        InspectionWorkOrderDetail d = detailMapper.selectById(req.getDetailId());
        if (d == null) {
            throw new IllegalArgumentException("步骤不存在");
        }
        if (!"stop".equalsIgnoreCase(d.getStepType())) {
            throw new IllegalArgumentException("仅停靠步骤可扫码关联设备");
        }
        InspectionWorkOrder order = orderMapper.selectById(d.getOrderId());
        assertExecutableOrder(order);
        assertCurrentStep(order.getId(), d);

        GridDevice dev = gridDeviceMapper.selectOne(new QueryWrapper<GridDevice>().eq("device_code", req.getDeviceCode().trim()));
        if (dev == null) {
            DeviceInfo di = deviceInfoMapper.selectOne(new QueryWrapper<DeviceInfo>().eq("device_code", req.getDeviceCode().trim()));
            if (di != null) {
                ensureDeviceRow(di.getId());
                dev = gridDeviceMapper.selectById(di.getId());
            }
        }
        if (dev == null) {
            throw new IllegalArgumentException("未找到设备编号: " + req.getDeviceCode());
        }
        if (d.getDeviceId() != null && !d.getDeviceId().equals(dev.getId())) {
            throw new IllegalArgumentException("该步骤已绑定其他设备");
        }
        d.setDeviceId(dev.getId());
        d.setDeviceName(dev.getDeviceName());
        detailMapper.updateById(d);
    }

    private void validateTaskBinding(InspectionWorkOrderCreateRequest req) {
        if (req.getTaskId() == null) {
            return;
        }
        InspectionTask t = taskMapper.selectById(req.getTaskId());
        if (t == null) {
            throw new IllegalArgumentException("关联任务不存在");
        }
        if (t.getWorkOrderId() != null) {
            throw new IllegalArgumentException("该任务已绑定巡检工单");
        }
        if (req.getPlanId() != null && t.getPlanId() != null && !req.getPlanId().equals(t.getPlanId())) {
            throw new IllegalArgumentException("计划与任务不匹配");
        }
    }

    private void bindTaskToWorkOrder(Long taskId, Long orderId) {
        InspectionTask t = taskMapper.selectById(taskId);
        if (t == null) {
            return;
        }
        if (t.getWorkOrderId() != null && !t.getWorkOrderId().equals(orderId)) {
            throw new IllegalArgumentException("该任务已绑定其他工单");
        }
        t.setWorkOrderId(orderId);
        taskMapper.updateById(t);
    }

    private InspectionWorkOrderCreateRequest buildDispatchWorkOrderRequest(
            InspectionPlan plan, InspectionTask task, DeviceInfo dev, DateTimeFormatter dtf) {
        LocalDateTime start = plan.getStartTime() != null ? plan.getStartTime() : task.getPlanExecuteTime();
        LocalDateTime end = plan.getEndTime() != null ? plan.getEndTime() : (start != null ? start.plusDays(7) : LocalDateTime.now().plusDays(7));
        if (start == null) {
            start = LocalDateTime.now();
        }
        InspectionWorkOrderCreateRequest req = new InspectionWorkOrderCreateRequest();
        req.setArea(plan.getPlanName());
        req.setShiftType(1);
        req.setInspectorId(task.getExecUserId());
        req.setInspectorName(resolveInspectorName(task.getExecUserId()));
        req.setPlanStartTime(dtf.format(start));
        req.setPlanEndTime(dtf.format(end));
        req.setRemark("由计划「" + plan.getPlanName() + "」派发，任务 " + task.getTaskCode());
        req.setPlanId(plan.getId());
        req.setTaskId(task.getId());
        req.setSteps(buildDefaultDispatchSteps(plan.getPlanName(), task, dev));
        return req;
    }

    private List<InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft> buildDefaultDispatchSteps(
            String areaName, InspectionTask task, DeviceInfo dev) {
        String dname = dev != null ? dev.getDeviceName() : ("设备ID " + task.getDeviceId());
        Long devId = task.getDeviceId();
        List<InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft> list = new ArrayList<>();
        int n = 1;
        InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft s1 = new InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft();
        s1.setStepOrder(n++);
        s1.setType("path");
        s1.setTarget(areaName);
        s1.setDescription("按路径前往 " + dname);
        s1.setDeviceId(devId);
        s1.setDeviceName(dname);
        list.add(s1);
        InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft s2 = new InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft();
        s2.setStepOrder(n++);
        s2.setType("stop");
        s2.setTarget(dev != null && StringUtils.hasText(dev.getLocation()) ? dev.getLocation() : areaName);
        s2.setDescription("停靠 " + dname + "，请扫码确认设备");
        s2.setDeviceName(dname);
        list.add(s2);
        InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft s3 = new InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft();
        s3.setStepOrder(n++);
        s3.setType("collect");
        s3.setDeviceId(devId);
        s3.setDeviceName(dname);
        s3.setCheckItem("巡检确认");
        s3.setDescription("执行规范巡检并记录");
        list.add(s3);
        InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft s4 = new InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft();
        s4.setStepOrder(n++);
        s4.setType("report");
        s4.setDescription(dname + " 本段巡检上报");
        s4.setDeviceId(devId);
        s4.setDeviceName(dname);
        list.add(s4);
        return list;
    }

    private String resolveInspectorName(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return null;
        }
        if (StringUtils.hasText(u.getNickname())) {
            return u.getNickname();
        }
        return u.getUsername();
    }

    // --- internal ---

    private void validateSteps(List<InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("步骤不能为空");
        }
        steps.sort(Comparator.comparingInt(InspectionWorkOrderCreateRequest.InspectionWorkOrderStepDraft::getStepOrder));
        for (int i = 0; i < steps.size(); i++) {
            if (!Objects.equals(steps.get(i).getStepOrder(), i + 1)) {
                throw new IllegalArgumentException("步骤序号须从 1 起连续递增");
            }
            String t = normalizeType(steps.get(i).getType());
            if (!List.of("path", "stop", "collect", "report").contains(t)) {
                throw new IllegalArgumentException("非法步骤类型: " + steps.get(i).getType());
            }
        }
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase();
    }

    private String mergeDescriptionWithMethod(String description, String detectionMethod) {
        String base = description == null ? "" : description.trim();
        if (!StringUtils.hasText(detectionMethod)) {
            return base.isEmpty() ? null : base;
        }
        String tag = METHOD_TAG + detectionMethod.trim();
        if (base.isEmpty()) {
            return tag;
        }
        return base + "\n" + tag;
    }

    private void splitDescriptionForVO(String stored, InspectionWorkOrderStepVO vo) {
        if (!StringUtils.hasText(stored)) {
            vo.setDescription(null);
            vo.setDetectionMethod(null);
            return;
        }
        int idx = stored.indexOf(METHOD_TAG);
        if (idx < 0) {
            vo.setDescription(stored);
            vo.setDetectionMethod(null);
            return;
        }
        vo.setDescription(stored.substring(0, idx).trim());
        vo.setDetectionMethod(stored.substring(idx + METHOD_TAG.length()).trim());
    }

    private LocalDateTime parseDt(String s) {
        return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private String genOrderNo() {
        String day = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "XJ-DW" + day;
        for (int k = 0; k < 20; k++) {
            String cand = prefix + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            Long cnt = orderMapper.selectCount(new QueryWrapper<InspectionWorkOrder>().eq("order_no", cand));
            if (cnt == null || cnt == 0) {
                return cand;
            }
        }
        throw new IllegalStateException("生成工单号失败，请重试");
    }

    private Map<Long, GridDevice> loadDeviceMap(List<InspectionWorkOrderDetail> details) {
        List<Long> ids = details.stream().map(InspectionWorkOrderDetail::getDeviceId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return gridDeviceMapper.selectList(new QueryWrapper<GridDevice>().in("id", ids)).stream()
                .collect(Collectors.toMap(GridDevice::getId, x -> x));
    }

    private InspectionWorkOrderStepVO toStepVO(InspectionWorkOrderDetail d, Map<Long, GridDevice> deviceMap) {
        InspectionWorkOrderStepVO vo = new InspectionWorkOrderStepVO();
        vo.setId(d.getId());
        vo.setOrderId(d.getOrderId());
        vo.setStepOrder(d.getStepOrder());
        vo.setType(d.getStepType());
        vo.setTarget(d.getTarget());
        splitDescriptionForVO(d.getDescription(), vo);
        vo.setDeviceId(d.getDeviceId());
        vo.setDeviceName(d.getDeviceName());
        vo.setVoltageLevel(null);
        if (d.getDeviceId() != null) {
            GridDevice g = deviceMap.get(d.getDeviceId());
            if (g != null) {
                vo.setDeviceCode(g.getDeviceCode());
            }
        }
        vo.setCheckItem(d.getCheckItem());
        vo.setStandardMin(d.getStandardMin());
        vo.setStandardMax(d.getStandardMax());
        vo.setUnit(d.getUnit());
        vo.setActualValue(d.getActualValue());
        vo.setIsException(d.getIsException());
        vo.setCollectTime(d.getCollectTime());
        vo.setPhotoUrl(d.getPhotoUrl());
        vo.setRemark(d.getRemark());
        return vo;
    }

    private boolean stepDone(InspectionWorkOrderDetail d) {
        if ("collect".equalsIgnoreCase(d.getStepType())) {
            return StringUtils.hasText(d.getActualValue());
        }
        return d.getCollectTime() != null;
    }

    private InspectionWorkOrderDetail findCurrentStep(Long orderId) {
        List<InspectionWorkOrderDetail> list = detailMapper.selectList(
                new QueryWrapper<InspectionWorkOrderDetail>().eq("order_id", orderId).orderByAsc("step_order"));
        for (InspectionWorkOrderDetail d : list) {
            if (!stepDone(d)) {
                return d;
            }
        }
        return null;
    }

    private void assertCurrentStep(Long orderId, InspectionWorkOrderDetail d) {
        InspectionWorkOrderDetail cur = findCurrentStep(orderId);
        if (cur == null) {
            throw new IllegalArgumentException("工单步骤已全部完成");
        }
        if (!cur.getId().equals(d.getId())) {
            throw new IllegalArgumentException("请按顺序执行，当前应处理步骤序号: " + cur.getStepOrder());
        }
    }

    private void assertExecutableOrder(InspectionWorkOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (order.getStatus() == InspectionWorkOrderStatus.CANCELLED || order.getStatus() == InspectionWorkOrderStatus.FINISHED) {
            throw new IllegalArgumentException("工单已结束，不可继续执行");
        }
        if (order.getStatus() == InspectionWorkOrderStatus.PENDING_ISSUE) {
            throw new IllegalArgumentException("工单待下发，请先下发");
        }
    }

    /**
     * 根据步骤完成情况推进主单状态与实际起止时间。
     */
    private void refreshOrderProgress(Long orderId) {
        InspectionWorkOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        if (order.getStatus() == InspectionWorkOrderStatus.CANCELLED
                || order.getStatus() == InspectionWorkOrderStatus.FINISHED
                || order.getStatus() == InspectionWorkOrderStatus.PENDING_ISSUE) {
            return;
        }
        List<InspectionWorkOrderDetail> list = detailMapper.selectList(
                new QueryWrapper<InspectionWorkOrderDetail>().eq("order_id", orderId).orderByAsc("step_order"));
        boolean anyDone = list.stream().anyMatch(this::stepDone);
        boolean allDone = list.stream().allMatch(this::stepDone);

        if (anyDone && order.getActualStartTime() == null) {
            order.setActualStartTime(LocalDateTime.now());
        }
        if (anyDone && order.getStatus() == InspectionWorkOrderStatus.PENDING_EXEC) {
            order.setStatus(InspectionWorkOrderStatus.RUNNING);
        }
        if (allDone) {
            order.setStatus(InspectionWorkOrderStatus.FINISHED);
            order.setActualEndTime(LocalDateTime.now());
        }
        orderMapper.updateById(order);
    }

    private BigDecimal parseNumericActual(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim().replaceAll("[^0-9.+-]", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据标准上下限判定是否异常；无法解析为数字时不标异常。
     */
    private int computeException(BigDecimal min, BigDecimal max, BigDecimal val) {
        if (val == null) {
            return 0;
        }
        if (min != null && max != null) {
            if (val.compareTo(min) < 0 || val.compareTo(max) > 0) {
                return 1;
            }
            return 0;
        }
        if (min != null && val.compareTo(min) < 0) {
            return 1;
        }
        if (max != null && val.compareTo(max) > 0) {
            return 1;
        }
        return 0;
    }
}
