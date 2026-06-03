package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.system.cmms.constants.InspectionWorkOrderStatus;
import com.raccoon.cloud.system.cmms.dto.AgentSimulationCompleteRequest;
import com.raccoon.cloud.system.cmms.entity.DeviceInfo;
import com.raccoon.cloud.system.cmms.entity.InspectionPoint;
import com.raccoon.cloud.system.cmms.entity.InspectionRecord;
import com.raccoon.cloud.system.cmms.entity.InspectionTask;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrder;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrderDetail;
import com.raccoon.cloud.system.cmms.mapper.DeviceInfoMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionPointMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionRecordMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionTaskMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderDetailMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderMapper;
import com.raccoon.common.dto.planning.SimulationAiResultDTO;
import com.raccoon.common.dto.planning.SimulationMissionReportDTO;
import com.raccoon.common.dto.planning.SimulationMultimodalDTO;
import com.raccoon.common.dto.planning.SimulationPhotoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将仿真/无人机巡检结果同步到 CMMS 工单步骤与巡检记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSimulationResultSyncService {

    private static final String WP_TAG = "waypointIndex=";
    private static final String DRONE_REPORT_PREFIX = "DRONE_REPORT:";

    private final InspectionWorkOrderMapper orderMapper;
    private final InspectionWorkOrderDetailMapper detailMapper;
    private final InspectionTaskMapper taskMapper;
    private final InspectionPointMapper pointMapper;
    private final InspectionRecordMapper recordMapper;
    private final DeviceInfoMapper deviceInfoMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void sync(AgentSimulationCompleteRequest req, InspectionWorkOrder order) {
        SimulationMissionReportDTO report = req.getMissionReport();
        Map<Integer, List<SimulationMultimodalDTO>> mmByWp = groupMultimodal(report);
        Map<Integer, SimulationPhotoDTO> photoByWp = indexPhotos(report);
        Map<String, SimulationAiResultDTO> aiByPhoto = indexAi(report);

        List<InspectionWorkOrderDetail> steps = detailMapper.selectList(
                new QueryWrapper<InspectionWorkOrderDetail>()
                        .eq("order_id", order.getId())
                        .orderByAsc("step_order"));
        LocalDateTime now = LocalDateTime.now();
        boolean anyAbnormal = false;

        for (InspectionWorkOrderDetail d : steps) {
            if (stepDone(d)) {
                continue;
            }
            String st = d.getStepType() != null ? d.getStepType().toLowerCase() : "";
            int wpIdx = parseWaypointIndex(d.getRemark());

            if ("report".equals(st)) {
                fillReportStep(d, report, order, now);
                detailMapper.updateById(d);
                continue;
            }

            if ("stop".equals(st) && d.getDeviceId() == null && StringUtils.hasText(d.getDeviceName())) {
                DeviceInfo di = deviceInfoMapper.selectOne(
                        new QueryWrapper<DeviceInfo>().eq("device_name", d.getDeviceName().trim()).last("LIMIT 1"));
                if (di != null) {
                    d.setDeviceId(di.getId());
                }
            }

            if ("collect".equals(st)) {
                CollectFill fill = buildCollectFill(d, wpIdx, mmByWp, photoByWp, aiByPhoto);
                d.setActualValue(fill.actualValue());
                d.setIsException(fill.isException());
                d.setPhotoUrl(truncate(fill.photoUrl(), 1000));
                if (StringUtils.hasText(fill.detailRemark())) {
                    d.setRemark(mergeRemark(d.getRemark(), fill.detailRemark()));
                }
                anyAbnormal = anyAbnormal || fill.isException() == 1;
            } else if (!"collect".equals(st)) {
                d.setRemark(mergeRemark(d.getRemark(), "无人机已执行"));
            }

            d.setCollectTime(now);
            detailMapper.updateById(d);
        }

        refreshOrderProgress(order.getId());
        syncInspectionRecords(order, report, mmByWp, photoByWp, anyAbnormal, now);
        log.info("[AgentSimSync] orderNo={} records synced", order.getOrderNo());
    }

    private void fillReportStep(InspectionWorkOrderDetail d, SimulationMissionReportDTO report,
                                InspectionWorkOrder order, LocalDateTime now) {
        StringBuilder desc = new StringBuilder();
        if (report != null) {
            desc.append(String.format("飞行 %.0fs，航程 %.1fm，拍照 %d 次，遥测 %d 条",
                    report.getDurationSec() != null ? report.getDurationSec() : 0,
                    report.getDistanceM() != null ? report.getDistanceM() : 0,
                    report.getPhotoCount() != null ? report.getPhotoCount()
                            : (report.getPhotos() != null ? report.getPhotos().size() : 0),
                    report.getTelemetrySent() != null ? report.getTelemetrySent() : 0));
            if (Boolean.TRUE.equals(report.getMultimodalUploaded())) {
                desc.append("；多模态已上报云端");
            } else if (StringUtils.hasText(report.getMultimodalError())) {
                desc.append("；多模态上报：").append(report.getMultimodalError());
            }
            if (report.getAiResults() != null) {
                long defects = report.getAiResults().stream()
                        .filter(a -> Boolean.TRUE.equals(a.getHasDefect())).count();
                if (defects > 0) {
                    desc.append("；AI检出异常 ").append(defects).append(" 处");
                } else {
                    desc.append("；AI检测未见异常");
                }
            }
        } else {
            desc.append("无人机巡检任务已完成（无详细上报载荷）");
        }
        d.setDescription(truncate(desc.toString(), 500));
        d.setCollectTime(now);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderNo", order.getOrderNo());
            payload.put("dispatchTaskId", order.getDispatchTaskId());
            if (report != null) {
                payload.put("summary", report);
            }
            d.setRemark(truncate(DRONE_REPORT_PREFIX + objectMapper.writeValueAsString(payload), 500));
        } catch (JsonProcessingException e) {
            d.setRemark(truncate(DRONE_REPORT_PREFIX + desc, 500));
        }
    }

    private void syncInspectionRecords(InspectionWorkOrder order, SimulationMissionReportDTO report,
                                       Map<Integer, List<SimulationMultimodalDTO>> mmByWp,
                                       Map<Integer, SimulationPhotoDTO> photoByWp,
                                       boolean anyAbnormal, LocalDateTime now) {
        if (order.getTaskId() == null) {
            return;
        }
        InspectionTask task = taskMapper.selectById(order.getTaskId());
        if (task == null) {
            return;
        }
        recordMapper.delete(new QueryWrapper<InspectionRecord>().eq("task_id", order.getTaskId()));

        List<Long> deviceIds = detailMapper.selectList(
                        new QueryWrapper<InspectionWorkOrderDetail>().eq("order_id", order.getId()))
                .stream()
                .map(InspectionWorkOrderDetail::getDeviceId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (deviceIds.isEmpty() && task.getDeviceId() != null) {
            deviceIds = List.of(task.getDeviceId());
        }

        int wpFallback = 0;
        for (Long deviceId : deviceIds) {
            List<InspectionPoint> points = pointMapper.selectList(
                    new QueryWrapper<InspectionPoint>()
                            .eq("device_id", deviceId)
                            .orderByAsc("sort", "id"));
            for (InspectionPoint p : points) {
                int wp = wpFallback;
                CollectFill fill = buildCollectFillForPoint(p, wp, mmByWp, photoByWp, Map.of());
                InspectionRecord r = new InspectionRecord();
                r.setTaskId(order.getTaskId());
                r.setDeviceId(deviceId);
                r.setPointId(p.getId());
                r.setCheckValue(fill.actualValue());
                r.setIsNormal(fill.isException() == 1 ? 0 : 1);
                if (StringUtils.hasText(fill.photoUrl())) {
                    try {
                        r.setImageUrls(objectMapper.writeValueAsString(List.of(truncate(fill.photoUrl(), 800))));
                    } catch (JsonProcessingException e) {
                        r.setImageUrls("[]");
                    }
                }
                r.setCreateTime(now);
                recordMapper.insert(r);
            }
            wpFallback++;
        }

        task.setStatus(2);
        task.setActualExecuteTime(now);
        task.setIsAbnormal(anyAbnormal ? 1 : 0);
        taskMapper.updateById(task);
    }

    private CollectFill buildCollectFill(InspectionWorkOrderDetail d, int wpIdx,
                                         Map<Integer, List<SimulationMultimodalDTO>> mmByWp,
                                         Map<Integer, SimulationPhotoDTO> photoByWp,
                                         Map<String, SimulationAiResultDTO> aiByPhoto) {
        return buildCollectFillForPoint(
                toVirtualPoint(d), wpIdx, mmByWp, photoByWp, aiByPhoto);
    }

    private CollectFill buildCollectFillForPoint(InspectionPoint p, int wpIdx,
                                                 Map<Integer, List<SimulationMultimodalDTO>> mmByWp,
                                                 Map<Integer, SimulationPhotoDTO> photoByWp,
                                                 Map<String, SimulationAiResultDTO> aiByPhoto) {
        List<SimulationMultimodalDTO> samples = mmByWp.getOrDefault(wpIdx, List.of());
        SimulationPhotoDTO photo = photoByWp.get(wpIdx);
        String checkItem = p.getPointName() != null ? p.getPointName() : "";

        String actual = simulateDefaultValue(p);
        int isEx = 0;
        String photoUrl = null;
        StringBuilder detail = new StringBuilder();

        for (SimulationMultimodalDTO s : samples) {
            String mt = s.getModalityType() != null ? s.getModalityType() : "";
            if (matchesCheckItem(checkItem, mt)) {
                actual = formatModalityValue(mt, s.getPayload());
                isEx = evaluateException(p, actual);
                if (StringUtils.hasText(s.getPreviewDataUrl())) {
                    photoUrl = s.getPreviewDataUrl();
                }
            }
        }

        if (photo != null && photoUrl == null && StringUtils.hasText(photo.getPreviewDataUrl())) {
            photoUrl = photo.getPreviewDataUrl();
        }

        if (photo != null && aiByPhoto != null) {
            SimulationAiResultDTO ai = aiByPhoto.get(photo.getId());
            if (ai != null) {
                detail.append("AI:").append(ai.getLabel());
                if (ai.getConfidence() != null) {
                    detail.append(String.format("(%.0f%%)", ai.getConfidence() * 100));
                }
                if (Boolean.TRUE.equals(ai.getHasDefect())) {
                    isEx = 1;
                    if (!StringUtils.hasText(actual) || "合格".equals(actual)) {
                        actual = ai.getLabel();
                    }
                }
            }
        }

        if (photo != null && photo.getLongitude() != null) {
            detail.append(String.format(" GPS(%.6f,%.6f)", photo.getLongitude(), photo.getLatitude()));
        }

        return new CollectFill(actual, isEx, photoUrl, detail.toString());
    }

    private InspectionPoint toVirtualPoint(InspectionWorkOrderDetail d) {
        InspectionPoint p = new InspectionPoint();
        p.setPointName(d.getCheckItem());
        p.setMinThreshold(d.getStandardMin());
        p.setMaxThreshold(d.getStandardMax());
        p.setUnit(d.getUnit());
        return p;
    }

    private boolean matchesCheckItem(String checkItem, String modality) {
        if (!StringUtils.hasText(checkItem)) {
            return true;
        }
        String c = checkItem.toLowerCase();
        return switch (modality) {
            case "VISIBLE" -> c.contains("可见") || c.contains("外观") || c.contains("拍照");
            case "THERMAL" -> c.contains("热") || c.contains("温");
            case "VIBRATION" -> c.contains("振");
            case "AUDIO" -> c.contains("声");
            case "TEMPERATURE" -> c.contains("温");
            default -> false;
        };
    }

    private String formatModalityValue(String modality, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "已采集";
        }
        if ("THERMAL".equals(modality) && payload.get("maxC") != null) {
            return payload.get("maxC") + "℃";
        }
        if ("VIBRATION".equals(modality)) {
            Object axis = payload.get("axis");
            if (axis instanceof Map<?, ?> m) {
                Object z = m.get("z");
                if (z instanceof Map<?, ?> zm && zm.get("rms") != null) {
                    return zm.get("rms") + "mm/s";
                }
            }
        }
        if ("TEMPERATURE".equals(modality) && payload.get("maxC") != null) {
            return payload.get("maxC") + "℃";
        }
        if ("VISIBLE".equals(modality)) {
            return "可见光已采集";
        }
        return "已采集";
    }

    private int evaluateException(InspectionPoint p, String actual) {
        BigDecimal val = parseNumeric(actual);
        if (val == null) {
            return 0;
        }
        if (p.getMinThreshold() != null && val.compareTo(p.getMinThreshold()) < 0) {
            return 1;
        }
        if (p.getMaxThreshold() != null && val.compareTo(p.getMaxThreshold()) > 0) {
            return 1;
        }
        return 0;
    }

    private String simulateDefaultValue(InspectionPoint p) {
        if (p.getMinThreshold() != null && p.getMaxThreshold() != null) {
            BigDecimal mid = p.getMinThreshold().add(p.getMaxThreshold())
                    .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            String v = mid.stripTrailingZeros().toPlainString();
            return StringUtils.hasText(p.getUnit()) ? v + p.getUnit() : v;
        }
        return "合格";
    }

    private BigDecimal parseNumeric(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.replaceAll("[^0-9.+-]", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Integer, List<SimulationMultimodalDTO>> groupMultimodal(SimulationMissionReportDTO report) {
        Map<Integer, List<SimulationMultimodalDTO>> map = new HashMap<>();
        if (report == null || report.getMultimodalSamples() == null) {
            return map;
        }
        for (SimulationMultimodalDTO s : report.getMultimodalSamples()) {
            int wp = s.getWaypointIndex() != null ? s.getWaypointIndex() : 0;
            map.computeIfAbsent(wp, k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private Map<Integer, SimulationPhotoDTO> indexPhotos(SimulationMissionReportDTO report) {
        Map<Integer, SimulationPhotoDTO> map = new HashMap<>();
        if (report == null || report.getPhotos() == null) {
            return map;
        }
        for (SimulationPhotoDTO p : report.getPhotos()) {
            int wp = p.getWaypointIndex() != null ? p.getWaypointIndex() : 0;
            map.put(wp, p);
        }
        return map;
    }

    private Map<String, SimulationAiResultDTO> indexAi(SimulationMissionReportDTO report) {
        Map<String, SimulationAiResultDTO> map = new HashMap<>();
        if (report == null || report.getAiResults() == null) {
            return map;
        }
        for (SimulationAiResultDTO a : report.getAiResults()) {
            if (StringUtils.hasText(a.getPhotoId())) {
                map.put(a.getPhotoId(), a);
            }
        }
        return map;
    }

    private int parseWaypointIndex(String remark) {
        if (!StringUtils.hasText(remark)) {
            return 0;
        }
        int i = remark.indexOf(WP_TAG);
        if (i < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(remark.substring(i + WP_TAG.length()).split("[|,\\s]")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean stepDone(InspectionWorkOrderDetail d) {
        if ("collect".equalsIgnoreCase(d.getStepType())) {
            return StringUtils.hasText(d.getActualValue());
        }
        return d.getCollectTime() != null;
    }

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

    private String mergeRemark(String existing, String line) {
        if (!StringUtils.hasText(existing)) {
            return line;
        }
        if (existing.contains(line)) {
            return existing;
        }
        return truncate(existing + " | " + line, 500);
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 3) + "...";
    }

    private record CollectFill(String actualValue, int isException, String photoUrl, String detailRemark) {
    }
}
