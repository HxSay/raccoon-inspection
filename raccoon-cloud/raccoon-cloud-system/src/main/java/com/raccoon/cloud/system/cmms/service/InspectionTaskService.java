package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.system.cmms.dto.InspectionTaskPageRequest;
import com.raccoon.cloud.system.cmms.dto.TaskCompleteRequest;
import com.raccoon.cloud.system.cmms.entity.DeviceInfo;
import com.raccoon.cloud.system.cmms.entity.InspectionPlan;
import com.raccoon.cloud.system.cmms.entity.InspectionPoint;
import com.raccoon.cloud.system.cmms.entity.InspectionRecord;
import com.raccoon.cloud.system.cmms.entity.InspectionTask;
import com.raccoon.cloud.system.cmms.entity.WorkOrder;
import com.raccoon.cloud.system.cmms.mapper.DeviceInfoMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionPlanMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionPointMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionRecordMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionTaskMapper;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class InspectionTaskService {

    private final InspectionTaskMapper taskMapper;
    private final InspectionPlanMapper planMapper;
    private final DeviceInfoMapper deviceInfoMapper;
    private final InspectionRecordMapper recordMapper;
    private final InspectionPointMapper pointMapper;
    private final ObjectMapper objectMapper;
    private final WorkOrderService workOrderService;
    private final UserService userService;
    private final InspectionWorkOrderService inspectionWorkOrderService;

    public InspectionTaskService(InspectionTaskMapper taskMapper,
                                 InspectionPlanMapper planMapper,
                                 DeviceInfoMapper deviceInfoMapper,
                                 InspectionRecordMapper recordMapper,
                                 InspectionPointMapper pointMapper,
                                 ObjectMapper objectMapper,
                                 WorkOrderService workOrderService,
                                 UserService userService,
                                 InspectionWorkOrderService inspectionWorkOrderService) {
        this.taskMapper = taskMapper;
        this.planMapper = planMapper;
        this.deviceInfoMapper = deviceInfoMapper;
        this.recordMapper = recordMapper;
        this.pointMapper = pointMapper;
        this.objectMapper = objectMapper;
        this.workOrderService = workOrderService;
        this.userService = userService;
        this.inspectionWorkOrderService = inspectionWorkOrderService;
    }

    public InspectionTask get(Long id) {
        return taskMapper.selectById(id);
    }

    public IPage<InspectionTask> page(InspectionTaskPageRequest req) {
        QueryWrapper<InspectionTask> w = new QueryWrapper<>();
        if (StringUtils.hasText(req.getTaskCode())) {
            w.like("task_code", req.getTaskCode());
        }
        if (req.getStatus() != null) {
            w.eq("status", req.getStatus());
        }
        if (req.getDeviceId() != null) {
            w.eq("device_id", req.getDeviceId());
        }
        if (req.getExecUserId() != null) {
            w.eq("exec_user_id", req.getExecUserId());
        }
        if (req.getPlanId() != null) {
            w.eq("plan_id", req.getPlanId());
        }
        w.orderByDesc("plan_execute_time", "id");
        Page<InspectionTask> p = new Page<>(req.getPage(), req.getSize());
        return taskMapper.selectPage(p, w);
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(InspectionTask row) {
        Long oldWoId = null;
        if (row.getId() != null) {
            InspectionTask old = taskMapper.selectById(row.getId());
            if (old != null) {
                oldWoId = old.getWorkOrderId();
            }
        }
        if (row.getStatus() == null) {
            row.setStatus(0);
        }
        if (row.getIsAbnormal() == null) {
            row.setIsAbnormal(0);
        }
        if (!StringUtils.hasText(row.getTaskCode())) {
            row.setTaskCode(genTaskCode());
        }
        fillMissingDeviceId(row);
        if (row.getId() == null) {
            taskMapper.insert(row);
        } else {
            taskMapper.updateById(row);
        }
        Long newWoId = row.getWorkOrderId();
        if (!Objects.equals(oldWoId, newWoId) && oldWoId != null) {
            inspectionWorkOrderService.unlinkTaskFromWorkOrder(row.getId(), oldWoId);
        }
        if (newWoId != null) {
            inspectionWorkOrderService.linkWorkOrderToTask(row.getId(), newWoId);
        }
    }

    public void delete(Long id) {
        InspectionTask t = taskMapper.selectById(id);
        if (t != null && t.getWorkOrderId() != null) {
            inspectionWorkOrderService.unlinkTaskFromWorkOrder(t.getId(), t.getWorkOrderId());
        }
        taskMapper.deleteById(id);
    }

    /**
     * 根据计划为每个关联设备生成一条待执行任务（计划须启用且 device_ids 为 JSON 数组）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int generateTasksFromPlan(Long planId) throws Exception {
        InspectionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        if (plan.getStatus() == null || plan.getStatus() != 1) {
            throw new IllegalArgumentException("计划未启用");
        }
        if (!StringUtils.hasText(plan.getDeviceIds())) {
            throw new IllegalArgumentException("计划未配置设备列表 device_ids");
        }
        List<Long> deviceIds = objectMapper.readValue(plan.getDeviceIds(), new TypeReference<List<Long>>() {
        });
        int n = 0;
        LocalDateTime planTime = LocalDateTime.now();
        for (Long deviceId : deviceIds) {
            if (deviceId == null) {
                continue;
            }
            DeviceInfo dev = deviceInfoMapper.selectById(deviceId);
            if (dev == null) {
                continue;
            }
            InspectionTask t = new InspectionTask();
            t.setTaskCode(genTaskCode());
            t.setPlanId(planId);
            t.setDeviceId(deviceId);
            t.setTaskName(plan.getPlanName() + " - " + dev.getDeviceName());
            t.setExecUserId(plan.getExecUserId());
            t.setPlanExecuteTime(planTime);
            t.setStatus(0);
            t.setIsAbnormal(0);
            taskMapper.insert(t);
            n++;
        }
        return n;
    }

    /**
     * GPS 签到并可选扫码校验设备编号。
     */
    public void checkIn(Long taskId, BigDecimal longitude, BigDecimal latitude, String scannedDeviceCode) {
        if (taskId == null) {
            throw new IllegalArgumentException("缺少 taskId");
        }
        InspectionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        DeviceInfo dev = task.getDeviceId() != null ? deviceInfoMapper.selectById(task.getDeviceId()) : null;
        if (StringUtils.hasText(scannedDeviceCode) && dev != null
                && !scannedDeviceCode.trim().equals(dev.getDeviceCode())) {
            throw new IllegalArgumentException("扫码设备与任务设备不一致");
        }
        if (StringUtils.hasText(scannedDeviceCode) && dev == null) {
            throw new IllegalArgumentException("任务未关联设备，无法进行扫码校验");
        }
        task.setLongitude(longitude);
        task.setLatitude(latitude);
        taskMapper.updateById(task);
    }

    /**
     * 开始执行：待执行(0) → 执行中(1)。
     */
    public void startTask(Long taskId) {
        InspectionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != 0) {
            throw new IllegalArgumentException("仅待执行任务可开始");
        }
        task.setStatus(1);
        taskMapper.updateById(task);
    }

    /**
     * 异常一键上报：标记任务异常并生成维修工单。
     *
     * @return 新建工单 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long reportAbnormal(Long taskId, String faultDescription, String faultImageUrls) {
        if (taskId == null) {
            throw new IllegalArgumentException("缺少 taskId");
        }
        InspectionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        task.setIsAbnormal(1);
        taskMapper.updateById(task);
        if (task.getDeviceId() == null) {
            throw new IllegalArgumentException("任务未关联设备，无法生成维修工单");
        }
        User u = userService.getCurrentUser();
        WorkOrder wo = workOrderService.createFromInspection(
                task.getDeviceId(), u.getId(),
                StringUtils.hasText(faultDescription) ? faultDescription : "巡检异常上报",
                faultImageUrls);
        return wo.getId();
    }

    /**
     * 提交巡检结果：写入 inspection_record，并将任务置为已完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(TaskCompleteRequest req) {
        if (req.getTaskId() == null) {
            throw new IllegalArgumentException("缺少 taskId");
        }
        InspectionTask task = taskMapper.selectById(req.getTaskId());
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (req.getRecords() != null) {
            for (TaskCompleteRequest.RecordLine line : req.getRecords()) {
                Long deviceId = resolveDeviceIdForRecord(task, line.getPointId());
                InspectionRecord r = new InspectionRecord();
                r.setTaskId(task.getId());
                r.setDeviceId(deviceId);
                r.setPointId(line.getPointId());
                r.setCheckValue(line.getCheckValue());
                r.setIsNormal(line.getIsNormal() == null ? 1 : line.getIsNormal());
                r.setImageUrls(line.getImageUrls());
                recordMapper.insert(r);
            }
        }
        boolean abnormal = req.getRecords() != null && req.getRecords().stream()
                .anyMatch(l -> l.getIsNormal() != null && l.getIsNormal() == 0);
        task.setStatus(2);
        task.setActualExecuteTime(LocalDateTime.now());
        task.setIsAbnormal(abnormal ? 1 : 0);
        taskMapper.updateById(task);
        if (abnormal) {
            User u = userService.getCurrentUser();
            StringBuilder desc = new StringBuilder("巡检项异常：");
            if (req.getRecords() != null) {
                for (TaskCompleteRequest.RecordLine line : req.getRecords()) {
                    if (line.getIsNormal() != null && line.getIsNormal() == 0) {
                        desc.append("[点").append(line.getPointId()).append("=").append(line.getCheckValue()).append("] ");
                    }
                }
            }
            String images = "[]";
            if (req.getRecords() != null) {
                String urls = req.getRecords().stream()
                        .map(TaskCompleteRequest.RecordLine::getImageUrls)
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null);
                if (urls != null) {
                    images = urls;
                }
            }
            Long deviceId = task.getDeviceId();
            if (deviceId == null && req.getRecords() != null) {
                for (TaskCompleteRequest.RecordLine line : req.getRecords()) {
                    if (line.getIsNormal() != null && line.getIsNormal() == 0) {
                        deviceId = resolveDeviceIdForRecord(task, line.getPointId());
                        break;
                    }
                }
            }
            if (deviceId == null) {
                throw new IllegalArgumentException("任务未关联设备且无法从巡检点解析设备，无法生成维修工单");
            }
            workOrderService.createFromInspection(deviceId, u.getId(), desc.toString(), images);
        }
    }

    /**
     * 扫描待执行且已到计划时间、已关联「待下发」巡检工单的任务，自动下发工单（手机端可见待执行）。
     */
    public int dispatchDueLinkedWorkOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<InspectionTask> list = taskMapper.selectList(new QueryWrapper<InspectionTask>()
                .eq("status", 0)
                .isNotNull("work_order_id")
                .le("plan_execute_time", now)
                .last("LIMIT 100"));
        int n = 0;
        for (InspectionTask t : list) {
            try {
                if (inspectionWorkOrderService.dispatchForScheduledTask(t.getWorkOrderId(), t.getExecUserId())) {
                    n++;
                }
            } catch (Exception e) {
                log.warn("任务 {} 关联工单 {} 到点派发失败: {}", t.getId(), t.getWorkOrderId(), e.getMessage());
            }
        }
        return n;
    }

    /**
     * 未传 deviceId 时：优先从关联巡检工单步骤解析，其次从计划 device_ids 取首个设备。
     */
    private void fillMissingDeviceId(InspectionTask row) {
        if (row.getDeviceId() != null) {
            return;
        }
        if (row.getWorkOrderId() != null) {
            Long fromWo = inspectionWorkOrderService.resolvePrimaryDeviceId(row.getWorkOrderId());
            if (fromWo != null) {
                row.setDeviceId(fromWo);
                return;
            }
        }
        if (row.getPlanId() == null) {
            return;
        }
        InspectionPlan plan = planMapper.selectById(row.getPlanId());
        if (plan == null || !StringUtils.hasText(plan.getDeviceIds())) {
            return;
        }
        try {
            List<Long> ids = objectMapper.readValue(plan.getDeviceIds(), new TypeReference<List<Long>>() {
            });
            for (Long id : ids) {
                if (id != null) {
                    row.setDeviceId(id);
                    return;
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("解析计划 device_ids 失败 planId={}: {}", row.getPlanId(), e.getMessage());
        }
    }

    private Long resolveDeviceIdForRecord(InspectionTask task, Long pointId) {
        if (task.getDeviceId() != null) {
            return task.getDeviceId();
        }
        if (pointId == null) {
            throw new IllegalArgumentException("缺少巡检点，无法解析设备");
        }
        InspectionPoint pt = pointMapper.selectById(pointId);
        if (pt == null || pt.getDeviceId() == null) {
            throw new IllegalArgumentException("任务未关联设备且巡检点未绑定设备，无法保存记录");
        }
        return pt.getDeviceId();
    }

    private String genTaskCode() {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        int r = (int) (Math.random() * 9000) + 1000;
        return "T" + ts + r;
    }
}
