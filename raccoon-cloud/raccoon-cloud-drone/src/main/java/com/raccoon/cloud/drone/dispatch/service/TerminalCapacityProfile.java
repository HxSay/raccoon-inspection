package com.raccoon.cloud.drone.dispatch.service;

import com.raccoon.cloud.drone.dispatch.enums.TerminalCapabilityLevelEnum;
import com.raccoon.cloud.drone.dispatch.model.TerminalCapacity;
import com.raccoon.cloud.drone.entity.UavInfo;
import com.raccoon.cloud.drone.entity.UavRobotRuntimeStatus;
import com.raccoon.cloud.drone.enums.InspectionRobotTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端能力画像构建器。
 * <p>根据机器人类型 + 实时电量推算 {@link TerminalCapacity}（运动、感知、续航三维度）。
 * 出厂参数表内置在本类，可后续抽到 yml 或独立数据库表（保持接口稳定）。
 *
 * @author raccoon
 */
@Slf4j
@Component
public class TerminalCapacityProfile {

    /**
     * 构建终端能力模型。
     *
     * @param uav      终端基础信息（非空）
     * @param runtime  实时状态（可空，缺失按默认参数估算）
     * @return 能力模型
     */
    public TerminalCapacity build(UavInfo uav, UavRobotRuntimeStatus runtime) {
        if (uav == null) {
            throw new IllegalArgumentException("终端基础信息不能为空");
        }
        TerminalCapacity capacity = new TerminalCapacity();
        capacity.setTerminalType(uav.getRobotType());

        applyTypeProfile(capacity, uav.getRobotType());

        if (runtime != null) {
            applyRuntimeAdjustment(capacity, runtime);
        }

        capacity.setOverallScore(computeOverall(capacity));
        capacity.setLevel(TerminalCapabilityLevelEnum.fromScore(capacity.getOverallScore()));
        return capacity;
    }

    /**
     * 按终端类型应用默认能力参数。
     * <p>无人机性能最优、机器狗适中、地面/轮式机器人偏低。
     */
    private void applyTypeProfile(TerminalCapacity capacity, String robotType) {
        InspectionRobotTypeEnum type = matchType(robotType);
        TerminalCapacity.Motion motion = capacity.getMotion();
        TerminalCapacity.Perception perception = capacity.getPerception();
        TerminalCapacity.Endurance endurance = capacity.getEndurance();

        switch (type) {
            case UAV -> {
                motion.setMaxSpeedMps(15.0).setMaxAccelMps2(3.5).setMaxRangeM(8000.0).setMaxAltitudeM(120.0);
                perception.setSensorTypes(sensors("VISIBLE_LIGHT", "THERMAL_IR", "ZOOM_CAMERA"))
                        .setCameraResolution(8000).setIrSupported(true).setLidarSupported(false);
                endurance.setDesignEnduranceMin(45).setCurrentEnduranceMin(45).setBatteryHealthPct(100f);
            }
            case ROBOT_DOG -> {
                motion.setMaxSpeedMps(3.5).setMaxAccelMps2(1.5).setMaxRangeM(2000.0).setMaxAltitudeM(2.0);
                perception.setSensorTypes(sensors("VISIBLE_LIGHT", "THERMAL_IR", "LIDAR", "MICROPHONE"))
                        .setCameraResolution(4000).setIrSupported(true).setLidarSupported(true);
                endurance.setDesignEnduranceMin(90).setCurrentEnduranceMin(90).setBatteryHealthPct(100f);
            }
            case WHEELED -> {
                motion.setMaxSpeedMps(2.0).setMaxAccelMps2(1.0).setMaxRangeM(1500.0).setMaxAltitudeM(1.0);
                perception.setSensorTypes(sensors("VISIBLE_LIGHT", "THERMAL_IR", "GAS"))
                        .setCameraResolution(3000).setIrSupported(true).setLidarSupported(false);
                endurance.setDesignEnduranceMin(120).setCurrentEnduranceMin(120).setBatteryHealthPct(100f);
            }
            case GROUND_ROBOT -> {
                motion.setMaxSpeedMps(1.5).setMaxAccelMps2(0.8).setMaxRangeM(1000.0).setMaxAltitudeM(1.0);
                perception.setSensorTypes(sensors("VISIBLE_LIGHT", "ULTRASONIC"))
                        .setCameraResolution(2000).setIrSupported(false).setLidarSupported(false);
                endurance.setDesignEnduranceMin(180).setCurrentEnduranceMin(180).setBatteryHealthPct(100f);
            }
            default -> log.debug("未识别终端类型 {}，使用默认能力", robotType);
        }
    }

    /** 根据实时电量/在线状态修正续航 */
    private void applyRuntimeAdjustment(TerminalCapacity capacity, UavRobotRuntimeStatus runtime) {
        Float batteryPct = runtime.getBatteryPct();
        Integer endurance = runtime.getEnduranceMin();
        TerminalCapacity.Endurance e = capacity.getEndurance();
        if (endurance != null && endurance >= 0) {
            e.setCurrentEnduranceMin(endurance);
        } else if (batteryPct != null) {
            int design = e.getDesignEnduranceMin();
            int current = Math.round(design * Math.max(0f, Math.min(1f, batteryPct / 100f)));
            e.setCurrentEnduranceMin(current);
        }
        if (batteryPct != null && batteryPct < 50f) {
            e.setBatteryHealthPct(Math.max(60f, batteryPct + 20f));
        }
    }

    /** 三维归一化加权综合得分 */
    private double computeOverall(TerminalCapacity capacity) {
        double motionScore = normalize(capacity.getMotion().getMaxSpeedMps(), 0.0, 20.0) * 0.4
                + normalize(capacity.getMotion().getMaxRangeM(), 0.0, 10000.0) * 0.6;
        double perceptionScore = normalize(capacity.getPerception().getSensorTypes().size(), 0, 6) * 0.5
                + normalize(capacity.getPerception().getCameraResolution(), 1000, 8000) * 0.3
                + (capacity.getPerception().isIrSupported() ? 0.1 : 0.0)
                + (capacity.getPerception().isLidarSupported() ? 0.1 : 0.0);
        double enduranceScore =
                normalize(capacity.getEndurance().getCurrentEnduranceMin(), 0, 180) * 0.7
                        + normalize(capacity.getEndurance().getBatteryHealthPct(), 60, 100) * 0.3;
        // 三维等权
        double overall = (motionScore + perceptionScore + enduranceScore) / 3.0;
        return Math.max(0.0, Math.min(1.0, overall));
    }

    private double normalize(double value, double lo, double hi) {
        if (hi - lo <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (value - lo) / (hi - lo)));
    }

    private InspectionRobotTypeEnum matchType(String code) {
        if (code == null) {
            return InspectionRobotTypeEnum.UAV;
        }
        for (InspectionRobotTypeEnum t : InspectionRobotTypeEnum.values()) {
            if (t.getCode().equalsIgnoreCase(code)) {
                return t;
            }
        }
        return InspectionRobotTypeEnum.UAV;
    }

    private List<String> sensors(String... arr) {
        List<String> list = new ArrayList<>();
        for (String s : arr) {
            if (s != null && !s.isBlank()) {
                list.add(s);
            }
        }
        return list;
    }
}
