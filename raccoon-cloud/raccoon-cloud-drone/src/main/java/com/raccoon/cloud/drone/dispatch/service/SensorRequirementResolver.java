package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.model.TerminalCapacity;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 传感器需求解析与终端能力匹配。
 * <p>把自然语言 / 规程关键词归一化为标准传感器编码（与 {@code TerminalCapacityProfile} 对齐），
 * 并判定某终端是否满足任务所需的传感器硬约束。
 *
 * @author raccoon
 */
@Component
public class SensorRequirementResolver {

    /**
     * 从自由文本中提取所需传感器编码（语义/规程关键词）。
     */
    public Set<String> fromText(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return result;
        }
        String t = text.toLowerCase();
        if (containsAny(t, "热成像", "红外", "测温", "温度异常", "thermal", "infrared", "ir")) {
            result.add("THERMAL_IR");
        }
        if (containsAny(t, "激光", "雷达", "点云", "三维建模", "lidar")) {
            result.add("LIDAR");
        }
        if (containsAny(t, "气体", "气体检测", "可燃气", "甲烷", "gas")) {
            result.add("GAS");
        }
        if (containsAny(t, "变焦", "长焦", "细节", "zoom")) {
            result.add("ZOOM_CAMERA");
        }
        return result;
    }

    /**
     * 把规范化传感器编码再做一次归一（容错 LLM 输出 THERMAL/红外 等别名）。
     */
    public String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim().toUpperCase();
        if (t.contains("THERMAL") || t.contains("IR") || t.contains("红外") || t.contains("热")) {
            return "THERMAL_IR";
        }
        if (t.contains("LIDAR") || t.contains("激光") || t.contains("雷达")) {
            return "LIDAR";
        }
        if (t.contains("GAS") || t.contains("气体")) {
            return "GAS";
        }
        if (t.contains("ZOOM") || t.contains("变焦") || t.contains("长焦")) {
            return "ZOOM_CAMERA";
        }
        if (t.contains("VISIBLE") || t.contains("可见光")) {
            return "VISIBLE_LIGHT";
        }
        return t;
    }

    /**
     * 终端是否满足某传感器要求。
     */
    public boolean terminalSupports(TerminalState terminal, String requiredSensor) {
        if (!StringUtils.hasText(requiredSensor) || terminal == null || terminal.getCapacity() == null) {
            return true;
        }
        String req = normalize(requiredSensor);
        TerminalCapacity.Perception p = terminal.getCapacity().getPerception();
        if (p == null) {
            return false;
        }
        if ("THERMAL_IR".equals(req) && p.isIrSupported()) {
            return true;
        }
        if ("LIDAR".equals(req) && p.isLidarSupported()) {
            return true;
        }
        List<String> sensors = p.getSensorTypes();
        if (sensors == null) {
            return false;
        }
        for (String s : sensors) {
            if (s != null && (s.equalsIgnoreCase(req) || normalize(s).equals(req))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回终端缺失的传感器集合（用于排除原因展示）。
     */
    public Set<String> missingSensors(TerminalState terminal, Set<String> required) {
        Set<String> missing = new LinkedHashSet<>();
        if (required == null) {
            return missing;
        }
        for (String r : required) {
            if (!terminalSupports(terminal, r)) {
                missing.add(normalize(r));
            }
        }
        return missing;
    }

    private boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
