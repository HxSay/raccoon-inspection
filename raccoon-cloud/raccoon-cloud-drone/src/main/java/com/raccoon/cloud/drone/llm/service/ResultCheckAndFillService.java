package com.raccoon.cloud.drone.llm.service;

import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.llm.catalog.InspectionCatalogService;
import com.raccoon.cloud.drone.llm.enums.PriorityEnum;
import com.raccoon.cloud.drone.llm.enums.TaskTypeEnum;
import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;
import com.raccoon.cloud.drone.llm.util.InspectionSlotNormalizer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 槽位校验、自动补全、缺失追问。
 */
@Slf4j
@Service
public class ResultCheckAndFillService {

    @Autowired
    private InspectionCatalogService catalogService;

    @Autowired
    private InspectionSlotNormalizer slotNormalizer;

    @Data
    public static class CheckResult {
        private LlmTaskSlotResult slots;
        private boolean needFollowUp;
        private String followUpQuestion;
        private Long resolvedMapId;
        private Long resolvedUavId;
        private List<UavInspectionDevice> resolvedDevices = new ArrayList<>();
    }

    public CheckResult checkAndFill(LlmTaskSlotResult slots) {
        return checkAndFill(slots, null);
    }

    /**
     * 槽位校验与补全；可传入用户原文以识别「所有杆塔」等全量巡检意图。
     */
    public CheckResult checkAndFill(LlmTaskSlotResult slots, String userInput) {
        CheckResult result = new CheckResult();
        result.setSlots(slots);

        if (StringUtils.hasText(userInput) && !Boolean.TRUE.equals(slots.getInspectAllDevices())
                && slotNormalizer.isInspectAllDevicesIntent(userInput)) {
            slots.setInspectAllDevices(true);
        }

        if (!StringUtils.hasText(slots.getTaskType())) {
            slots.setTaskType(TaskTypeEnum.REGULAR.name());
        }
        if (!StringUtils.hasText(slots.getPriority())) {
            slots.setPriority(PriorityEnum.NORMAL.name());
        }

        List<String> missing = new ArrayList<>();

        if (!StringUtils.hasText(slots.getAreaName())) {
            missing.add("巡检区域（areaName）");
        } else {
            Optional<UavMap> area = catalogService.findAreaByName(slots.getAreaName());
            if (area.isEmpty()) {
                missing.add("有效的巡检区域（系统中不存在：" + slots.getAreaName() + "）");
            } else {
                result.setResolvedMapId(area.get().getId());
                slots.setAreaName(area.get().getMapName());
                catalogService.findFirstUavByMap(area.get().getId())
                        .ifPresent(u -> result.setResolvedUavId(u.getId()));
            }
        }

        if (slots.getDeviceNames() == null || slots.getDeviceNames().isEmpty()) {
            if (Boolean.TRUE.equals(slots.getInspectAllDevices()) && result.getResolvedMapId() != null) {
                fillAllDevicesInArea(slots, result, userInput);
            } else {
                missing.add("巡检设备列表（deviceNames）");
            }
        } else if (result.getResolvedMapId() != null) {
            List<UavInspectionDevice> devices = catalogService.findDevicesByMapAndNames(
                    result.getResolvedMapId(), slots.getDeviceNames());
            if (devices.size() < slots.getDeviceNames().size()) {
                missing.add("全部设备名称需在系统中存在，请确认设备名称");
            } else {
                result.setResolvedDevices(devices);
                List<String> canonical = new ArrayList<>();
                for (UavInspectionDevice d : devices) {
                    canonical.add(d.getDeviceName());
                }
                slots.setDeviceNames(canonical);
            }
        }

        if (!missing.isEmpty()) {
            result.setNeedFollowUp(true);
            result.setFollowUpQuestion("请补充以下信息后再试：" + String.join("、", missing));
            log.info("槽位不完整，需追问: {}", result.getFollowUpQuestion());
        }

        return result;
    }

    /**
     * 「所有杆塔/全部设备」：自动展开场景内全部启用设备，优先杆塔类。
     */
    private void fillAllDevicesInArea(LlmTaskSlotResult slots, CheckResult result, String userInput) {
        List<UavInspectionDevice> all = catalogService.listDevicesByMap(result.getResolvedMapId());
        if (all.isEmpty()) {
            log.warn("inspectAllDevices=true 但场景 mapId={} 无设备", result.getResolvedMapId());
            return;
        }
        boolean towerOnly = userInput != null
                && (userInput.contains("杆塔") || userInput.contains("塔杆"))
                && !userInput.contains("设备");
        List<UavInspectionDevice> picked = all;
        if (towerOnly) {
            picked = all.stream()
                    .filter(d -> d.getDeviceType() != null
                            && ("TOWER".equalsIgnoreCase(d.getDeviceType())
                            || "tower".equalsIgnoreCase(d.getDeviceType())))
                    .collect(Collectors.toList());
            if (picked.isEmpty()) {
                picked = all.stream()
                        .filter(d -> d.getDeviceName() != null && d.getDeviceName().contains("杆塔"))
                        .collect(Collectors.toList());
            }
        }
        if (picked.isEmpty()) {
            picked = all;
        }
        result.setResolvedDevices(picked);
        List<String> names = new ArrayList<>();
        for (UavInspectionDevice d : picked) {
            names.add(d.getDeviceName());
        }
        slots.setDeviceNames(names);
        log.info("已自动展开全量巡检设备 mapId={} count={} names={}",
                result.getResolvedMapId(), names.size(), names);
    }
}
