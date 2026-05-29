package com.raccoon.cloud.drone.llm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 LLM 原始输出中提取 JSON 并映射槽位。
 */
public final class LlmJsonExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private LlmJsonExtractor() {
    }

    public static LlmTaskSlotResult parseSlotJson(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LLM 返回为空");
        }
        String json = extractJsonString(raw.trim());
        JsonNode node = MAPPER.readTree(json);
        LlmTaskSlotResult r = new LlmTaskSlotResult();
        r.setTaskType(text(node, "taskType"));
        r.setAreaName(text(node, "areaName"));
        r.setPriority(text(node, "priority"));
        r.setPlanTime(text(node, "planTime"));
        r.setRemark(text(node, "remark"));
        r.setDeviceNames(parseDeviceNames(node));
        r.setRecommendedDrones(intOrNull(node, "recommendedDrones"));
        r.setFleetReason(text(node, "fleetReason"));
        return r;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isInt() || v.isLong()) {
            return v.asInt();
        }
        if (v.isTextual()) {
            Matcher m = Pattern.compile("\\d+").matcher(v.asText());
            if (m.find()) {
                return Integer.parseInt(m.group());
            }
        }
        return null;
    }

    private static String extractJsonString(String raw) {
        Matcher m = JSON_BLOCK.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText().trim();
    }

    private static List<String> parseDeviceNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        JsonNode arr = node.get("deviceNames");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                if (n != null && !n.isNull() && StringUtils.hasText(n.asText())) {
                    names.add(n.asText().trim());
                }
            }
        }
        return names;
    }
}
