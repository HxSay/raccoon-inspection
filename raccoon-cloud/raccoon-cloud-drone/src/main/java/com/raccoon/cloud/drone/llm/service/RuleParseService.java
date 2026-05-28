package com.raccoon.cloud.drone.llm.service;

import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.llm.catalog.InspectionCatalogService;
import com.raccoon.cloud.drone.llm.enums.PriorityEnum;
import com.raccoon.cloud.drone.llm.enums.TaskTypeEnum;
import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;
import com.raccoon.cloud.drone.llm.util.InspectionSlotNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 规则兜底解析：关键词匹配任务类型/优先级，并匹配系统区域与设备。
 */
@Slf4j
@Service
public class RuleParseService {

    @Autowired
    private InspectionCatalogService catalogService;

    @Autowired
    private InspectionSlotNormalizer slotNormalizer;

    public LlmTaskSlotResult parse(String userInput) {
        log.warn("启用 RuleParseService 规则降级解析");
        LlmTaskSlotResult r = new LlmTaskSlotResult();
        r.setParseSource("RULE");

        String text = userInput;
        if (text.contains("复巡") || text.contains("复查")) {
            r.setTaskType(TaskTypeEnum.RE_INSPECTION.name());
            r.setPriority(PriorityEnum.URGENT.name());
        } else if (text.contains("紧急")) {
            r.setPriority(PriorityEnum.URGENT.name());
            r.setTaskType(TaskTypeEnum.REGULAR.name());
        } else if (text.contains("临时")) {
            r.setTaskType(TaskTypeEnum.TEMP.name());
            r.setPriority(PriorityEnum.NORMAL.name());
        } else {
            r.setTaskType(TaskTypeEnum.REGULAR.name());
            r.setPriority(PriorityEnum.NORMAL.name());
        }

        Optional<UavMap> area = matchArea(text);
        area.ifPresent(m -> r.setAreaName(m.getMapName()));

        if (area.isPresent()) {
            r.setDeviceNames(matchDeviceNames(text, area.get().getId()));
        } else {
            r.setDeviceNames(new ArrayList<>());
        }

        if (text.contains("明天") || text.contains("上午") || text.contains("下午")) {
            r.setPlanTime(extractPlanTimeHint(text));
        } else {
            r.setPlanTime("");
        }
        r.setRemark("规则解析");
        slotNormalizer.enrich(text, r);
        return r;
    }

    private Optional<UavMap> matchArea(String text) {
        for (String name : catalogService.listAllAreaNames()) {
            if (text.contains(name)) {
                return catalogService.findAreaByName(name);
            }
        }
        if (text.contains("输电") || text.contains("线路")) {
            return catalogService.findAreaByName("输电线路巡检场景");
        }
        if (text.contains("变电站")) {
            return catalogService.findAreaByName("变电站场景");
        }
        if (text.contains("热力")) {
            return catalogService.findAreaByName("热力管网场景");
        }
        if (text.contains("杆塔") || text.contains("塔杆")) {
            return catalogService.findAreaByName("输电线路巡检场景");
        }
        return Optional.empty();
    }

    private List<String> matchDeviceNames(String text, Long mapId) {
        List<String> found = new ArrayList<>();
        for (String name : slotNormalizer.extractTowerDeviceNames(text)) {
            if (!found.contains(name)) {
                found.add(name);
            }
        }
        String compact = text.replaceAll("\\s+", "");
        for (UavInspectionDevice d : catalogService.listDevicesByMap(mapId)) {
            if (compact.contains(d.getDeviceName()) && !found.contains(d.getDeviceName())) {
                found.add(d.getDeviceName());
            }
        }
        return found;
    }

    private String extractPlanTimeHint(String text) {
        if (text.contains("明天上午")) {
            return "明天上午";
        }
        if (text.contains("明天")) {
            return "明天";
        }
        if (text.contains("上午")) {
            return "上午";
        }
        if (text.contains("下午")) {
            return "下午";
        }
        return "";
    }
}
