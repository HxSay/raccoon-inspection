package com.raccoon.cloud.drone.llm.util;

import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 补全/规范化槽位：口语化「杆塔 1、2」、未写区域名等。
 */
@Component
public class InspectionSlotNormalizer {

    private static final String DEFAULT_PATROL_AREA = "输电线路巡检场景";

    private static final Pattern TOWER_SINGLE = Pattern.compile("(?:杆塔|塔杆|#?塔)\\s*(\\d+)");
    private static final Pattern TOWER_LIST_BLOCK =
            Pattern.compile("(?:杆塔|塔杆|#?塔)\\s*([\\d\\s、,，和及到至\\-]+)");

    public void enrich(String userInput, LlmTaskSlotResult slots) {
        if (slots == null || !StringUtils.hasText(userInput)) {
            return;
        }
        String text = userInput.trim();

        if (!StringUtils.hasText(slots.getAreaName()) && inferPatrolArea(text)) {
            slots.setAreaName(DEFAULT_PATROL_AREA);
        }

        List<String> extracted = extractTowerDeviceNames(text);
        if ((slots.getDeviceNames() == null || slots.getDeviceNames().isEmpty()) && !extracted.isEmpty()) {
            slots.setDeviceNames(extracted);
        } else if (slots.getDeviceNames() != null && !slots.getDeviceNames().isEmpty()) {
            List<String> normalized = new ArrayList<>();
            for (String name : slots.getDeviceNames()) {
                String n = normalizeDeviceName(name);
                if (StringUtils.hasText(n) && !normalized.contains(n)) {
                    normalized.add(n);
                }
            }
            if (normalized.isEmpty() && !extracted.isEmpty()) {
                normalized.addAll(extracted);
            }
            slots.setDeviceNames(normalized);
        }
    }

    /** 提及杆塔/输电/线路/巡检场景等时默认输电区域 */
    private boolean inferPatrolArea(String text) {
        if (text.contains("输电") || text.contains("线路") || text.contains("输电线路")) {
            return true;
        }
        if (text.contains("杆塔") || text.contains("塔杆")) {
            return true;
        }
        if (TOWER_SINGLE.matcher(text).find() || TOWER_LIST_BLOCK.matcher(text).find()) {
            return true;
        }
        return false;
    }

    public List<String> extractTowerDeviceNames(String text) {
        Set<Integer> indices = new LinkedHashSet<>();
        Matcher single = TOWER_SINGLE.matcher(text);
        while (single.find()) {
            indices.add(Integer.parseInt(single.group(1)));
        }
        Matcher block = TOWER_LIST_BLOCK.matcher(text);
        while (block.find()) {
            Matcher num = Pattern.compile("\\d+").matcher(block.group(1));
            while (num.find()) {
                indices.add(Integer.parseInt(num.group()));
            }
        }
        List<String> names = new ArrayList<>();
        for (int i : indices) {
            if (i >= 1 && i <= 5) {
                names.add("杆塔" + i);
            }
        }
        return names;
    }

    private String normalizeDeviceName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim().replaceAll("\\s+", "");
        Matcher m = Pattern.compile("(?:杆塔|塔杆|塔)(\\d+)").matcher(s);
        if (m.find()) {
            return "杆塔" + m.group(1);
        }
        return s;
    }
}
