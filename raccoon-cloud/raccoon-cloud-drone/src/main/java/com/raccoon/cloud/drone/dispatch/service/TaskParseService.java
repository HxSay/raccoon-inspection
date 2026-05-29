package com.raccoon.cloud.drone.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskRequest;
import com.raccoon.cloud.drone.dispatch.enums.DispatchPriorityEnum;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskStatusEnum;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskTypeEnum;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.llm.catalog.InspectionCatalogService;
import com.raccoon.cloud.drone.llm.dto.NlpTaskParseResponse;
import com.raccoon.cloud.drone.llm.model.InspectionTask;
import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;
import com.raccoon.cloud.drone.llm.service.NlpTaskParseFacadeService;
import com.raccoon.cloud.drone.llm.service.ResultCheckAndFillService;
import com.raccoon.cloud.drone.mapper.UavInspectionDeviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 调度中枢任务解析服务（步骤 1）。
 * <p>职责：
 * <ol>
 *   <li>接收 {@link DispatchTaskRequest} 原始请求</li>
 *   <li>融合 NLP / 结构化两路入参，转换为标准化 {@link DispatchInspectionTask}</li>
 *   <li>根据任务类型映射默认优先级；自动解析巡检范围并关联设备 ID</li>
 *   <li>初始化任务状态 {@link DispatchTaskStatusEnum#PARSED} 与创建时间</li>
 * </ol>
 * <p>不改变 {@link NlpTaskParseFacadeService} 现有逻辑，仅在结构化字段缺失时回退调用 NLP。
 *
 * @author raccoon
 */
@Slf4j
@Service
public class TaskParseService {

    @Autowired
    private InspectionCatalogService catalogService;

    @Autowired
    private NlpTaskParseFacadeService nlpTaskParseFacadeService;

    @Autowired
    private UavInspectionDeviceMapper deviceMapper;

    @Autowired
    private ResultCheckAndFillService resultCheckAndFillService;

    /**
     * 解析请求为标准化任务模型。
     *
     * @param request 原始请求（非空）
     * @return 标准化任务；当 NLP 仍需追问时抛出 IllegalArgumentException
     */
    public DispatchInspectionTask parse(DispatchTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("调度任务请求不能为空");
        }
        long start = System.currentTimeMillis();
        DispatchInspectionTask task = new DispatchInspectionTask();
        task.setTaskId(generateTaskId());
        task.setRequestId(StringUtils.hasText(request.getRequestId())
                ? request.getRequestId().trim() : task.getTaskId());
        task.setCreateTime(LocalDateTime.now());
        task.setRemark(request.getRemark());
        task.setPlanTime(request.getPlanTime());
        task.setDeadline(request.getDeadline());
        if (request.getExtension() != null) {
            task.getExtension().putAll(request.getExtension());
        }

        if (StringUtils.hasText(request.getUserInput())) {
            mergeNlpParse(request, task);
            task.setUserInput(request.getUserInput().trim());
        } else {
            mergeStructured(request, task);
            task.setParseSource("MANUAL");
        }

        applyDefaults(task);
        resolveDevices(task);
        applyRiskFactors(request, task);

        task.setStatus(DispatchTaskStatusEnum.PARSED);
        log.info("[task-parse] taskId={} type={} priority={} mapId={} devices={} elapsedMs={}",
                task.getTaskId(), task.getTaskType().getCode(), task.getPriority().getCode(),
                task.getMapId(), task.getDeviceIds().size(), System.currentTimeMillis() - start);
        return task;
    }

    /** 调用 NLP 服务并将解析结果合并入任务（保留原 NLP 服务的全部行为）。 */
    private void mergeNlpParse(DispatchTaskRequest request, DispatchInspectionTask task) {
        String raw = request.getUserInput().trim();
        task.setUserInput(raw);
        NlpTaskParseResponse response = nlpTaskParseFacadeService.parse(raw);
        task.setParseSource(response.getParseSource() != null ? response.getParseSource() : "LLM");
        if (response.isNeedFollowUp()) {
            log.warn("[task-parse] NLP 仍需追问: {}", response.getFollowUpQuestion());
            throw new IllegalArgumentException(
                    "任务信息不完整：" + Optional.ofNullable(response.getFollowUpQuestion()).orElse(""));
        }
        LlmTaskSlotResult slots = response.getSlots();
        if (slots != null) {
            task.setInspectAllDevices(slots.getInspectAllDevices());
            task.setAreaName(slots.getAreaName());
            task.setDeviceNames(slots.getDeviceNames() != null ? slots.getDeviceNames() : new ArrayList<>());
            task.setTaskType(DispatchTaskTypeEnum.parse(slots.getTaskType()));
            if (StringUtils.hasText(slots.getPriority())) {
                task.setPriority(DispatchPriorityEnum.fromLegacy(slots.getPriority()));
            }
            if (!StringUtils.hasText(task.getRemark()) && StringUtils.hasText(slots.getRemark())) {
                task.setRemark(slots.getRemark());
            }
        }
        InspectionTask inner = response.getTask();
        if (inner != null) {
            task.setMapId(inner.getMapId());
            if (inner.getTakeoff() != null) {
                task.getDeviceWaypoints().add(inner.getTakeoff());
            }
            if (inner.getWaypoints() != null) {
                task.getDeviceWaypoints().addAll(inner.getWaypoints());
            }
        }
        if (!CollectionUtils.isEmpty(request.getDeviceNames())) {
            task.setDeviceNames(mergeNames(task.getDeviceNames(), request.getDeviceNames()));
        }
        if (!CollectionUtils.isEmpty(request.getDeviceIds())) {
            task.setDeviceIds(distinct(request.getDeviceIds()));
        }
    }

    /** 直接合并结构化入参。 */
    private void mergeStructured(DispatchTaskRequest request, DispatchInspectionTask task) {
        task.setMapId(request.getMapId());
        task.setAreaName(request.getAreaName());
        if (!CollectionUtils.isEmpty(request.getDeviceNames())) {
            task.setDeviceNames(distinct(request.getDeviceNames()));
        }
        if (!CollectionUtils.isEmpty(request.getDeviceIds())) {
            task.setDeviceIds(distinct(request.getDeviceIds()));
        }
        if (!CollectionUtils.isEmpty(request.getWaypoints())) {
            task.getDeviceWaypoints().addAll(request.getWaypoints());
        }
        task.setTaskType(DispatchTaskTypeEnum.parse(request.getTaskType()));
        if (StringUtils.hasText(request.getPriority())) {
            task.setPriority(DispatchPriorityEnum.fromLegacy(request.getPriority()));
        }
    }

    /** 应用任务类型默认优先级（结构化未指定优先级时生效）。 */
    private void applyDefaults(DispatchInspectionTask task) {
        if (task.getPriority() == null) {
            task.setPriority(DispatchPriorityEnum.fromScore(task.getTaskType().getIntrinsicWeight()));
        }
        if (task.getDeviceNames() == null) {
            task.setDeviceNames(new ArrayList<>());
        }
        if (task.getDeviceIds() == null) {
            task.setDeviceIds(new ArrayList<>());
        }
    }

    /**
     * 自动关联设备 ID：
     * <ul>
     *   <li>已有 deviceIds 时校验合法性并补齐实体</li>
     *   <li>否则根据 mapId + deviceNames 查询设备表</li>
     * </ul>
     */
    private void resolveDevices(DispatchInspectionTask task) {
        if (task.getMapId() == null) {
            log.debug("[task-parse] mapId 缺失，跳过设备关联 taskId={}", task.getTaskId());
            return;
        }
        if (!task.getDeviceIds().isEmpty()) {
            List<UavInspectionDevice> rows = deviceMapper.selectList(
                    new LambdaQueryWrapper<UavInspectionDevice>()
                            .eq(UavInspectionDevice::getMapId, task.getMapId())
                            .in(UavInspectionDevice::getId, task.getDeviceIds()));
            task.setResolvedDevices(rows);
            return;
        }
        if (!CollectionUtils.isEmpty(task.getDeviceNames())) {
            List<UavInspectionDevice> rows = catalogService.findDevicesByMapAndNames(
                    task.getMapId(), task.getDeviceNames());
            rows = resultCheckAndFillService.expandPatrolTowersIfNeeded(
                    task.getMapId(), rows, task.getUserInput(), task.getInspectAllDevices());
            task.setResolvedDevices(rows);
            task.setDeviceNames(rows.stream().map(UavInspectionDevice::getDeviceName).toList());
            task.setDeviceIds(rows.stream().map(UavInspectionDevice::getId).toList());
        } else {
            List<UavInspectionDevice> all = catalogService.listDevicesByMap(task.getMapId());
            task.setResolvedDevices(all);
            task.setDeviceIds(all.stream().map(UavInspectionDevice::getId).toList());
        }
        if (!StringUtils.hasText(task.getAreaName())) {
            Optional.ofNullable(task.getMapId())
                    .flatMap(id -> catalogService.findAreaByName(""))
                    .map(UavMap::getMapName)
                    .ifPresent(task::setAreaName);
        }
        for (UavInspectionDevice d : task.getResolvedDevices()) {
            if (d.getLongitude() == null || d.getLatitude() == null) {
                continue;
            }
            GeoPoint p = new GeoPoint(d.getLongitude(), d.getLatitude(),
                    d.getHeight() == null ? 0.0 : d.getHeight());
            task.getDeviceWaypoints().add(p);
        }
    }

    /** 应用风险因子；缺失则使用任务类型默认值。 */
    private void applyRiskFactors(DispatchTaskRequest req, DispatchInspectionTask task) {
        task.setCriticalLevel(clamp01(req.getCriticalLevel(), 0.5));
        task.setRiskLevel(clamp01(req.getRiskLevel(), 0.3));
        task.setFaultHistoryRate(clamp01(req.getFaultHistoryRate(), 0.2));
        Double interval = req.getInspectionIntervalHours();
        if (interval == null || interval < 0) {
            interval = 24.0;
        }
        task.setInspectionIntervalHours(interval);
    }

    /** [0,1] 截断；空值用默认值 */
    private double clamp01(Double v, double def) {
        if (v == null) {
            return def;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** 生成任务 ID：DT- + 时间戳 + 4位随机 */
    private String generateTaskId() {
        return "DT-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);
    }

    /** 合并去重保持顺序 */
    private List<String> mergeNames(List<String> a, List<String> b) {
        Set<String> set = new LinkedHashSet<>();
        if (a != null) {
            a.stream().filter(StringUtils::hasText).map(String::trim).forEach(set::add);
        }
        if (b != null) {
            b.stream().filter(StringUtils::hasText).map(String::trim).forEach(set::add);
        }
        return new ArrayList<>(set);
    }

    private <T> List<T> distinct(List<T> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(list));
    }
}
