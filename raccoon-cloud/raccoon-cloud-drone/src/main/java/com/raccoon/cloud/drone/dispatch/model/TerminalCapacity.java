package com.raccoon.cloud.drone.dispatch.model;

import com.raccoon.cloud.drone.dispatch.enums.TerminalCapabilityLevelEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端能力模型：量化巡检设备在运动、感知、续航三大维度上的作业能力。
 * <p>由 TerminalStateService 在终端注册或上线时构建并随状态更新；用于：
 * <ul>
 *     <li>AuctionAssignService 拍卖中的能力匹配评分</li>
 *     <li>TaskPriorityService 对设备-任务匹配度加权</li>
 *     <li>GlobalPathPlanService 根据最大速度/航程裁剪可行路径</li>
 * </ul>
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class TerminalCapacity {

    /** 终端类型，与 {@code uav_info.robot_type} 对齐 */
    private String terminalType;

    /** 运动能力 */
    private Motion motion = new Motion();

    /** 感知能力 */
    private Perception perception = new Perception();

    /** 续航能力 */
    private Endurance endurance = new Endurance();

    /** 综合能力得分（0~1），由三大维度归一化加权 */
    private double overallScore;

    /** 综合能力等级 */
    private TerminalCapabilityLevelEnum level = TerminalCapabilityLevelEnum.MEDIUM;

    /**
     * 运动能力：影响到达耗时、路径可达性。
     */
    @Data
    @Accessors(chain = true)
    public static class Motion {
        /** 最大巡航速度 m/s */
        private double maxSpeedMps = 8.0;
        /** 最大加速度 m/s² */
        private double maxAccelMps2 = 2.0;
        /** 最大作业半径（米），超出此值无法到达 */
        private double maxRangeM = 5000.0;
        /** 最大作业高度（米） */
        private double maxAltitudeM = 120.0;
    }

    /**
     * 感知能力：摄像头/红外/激光雷达组合，决定能否承接特定巡检维度。
     */
    @Data
    @Accessors(chain = true)
    public static class Perception {
        /** 携带的传感器类型列表 */
        private List<String> sensorTypes = new ArrayList<>();
        /** 主摄像头分辨率（像素，单边） */
        private int cameraResolution = 4000;
        /** 是否支持红外热成像 */
        private boolean irSupported = true;
        /** 是否支持激光雷达 */
        private boolean lidarSupported = false;
    }

    /**
     * 续航能力：剩余可执行作业时长。
     */
    @Data
    @Accessors(chain = true)
    public static class Endurance {
        /** 设计续航分钟数（满电） */
        private int designEnduranceMin = 35;
        /** 当前剩余续航分钟数（按实时电量推算） */
        private int currentEnduranceMin = 35;
        /** 电池健康度 0~100 */
        private float batteryHealthPct = 100f;
    }
}
