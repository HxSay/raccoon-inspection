package com.raccoon.cloud.drone.dispatch.constant;

/**
 * 调度中枢通用常量。
 * <p>权重、阈值、超时均集中在此，便于压测调参与运维统一调整。
 *
 * @author raccoon
 */
public final class DispatchConstants {

    private DispatchConstants() {
    }

    // ============== 优先级权重（五维加权） ==============
    /** 故障历史权重 30% */
    public static final double WEIGHT_FAULT_HISTORY = 0.30;
    /** 负载权重 25% */
    public static final double WEIGHT_LOAD = 0.25;
    /** 关键等级权重 20% */
    public static final double WEIGHT_CRITICAL = 0.20;
    /** 风险等级权重 15% */
    public static final double WEIGHT_RISK = 0.15;
    /** 巡检间隔权重 10% */
    public static final double WEIGHT_INTERVAL = 0.10;

    // ============== 知识增强权重（命中知识时按比例让渡基础分） ==============
    /** 知识增强时，五维基础分的占比 75% */
    public static final double WEIGHT_KNOWLEDGE_BASE = 0.75;
    /** 规程约束紧迫度权重 15% */
    public static final double WEIGHT_REGULATION = 0.15;
    /** 图拓扑影响权重 5% */
    public static final double WEIGHT_TOPOLOGY = 0.05;
    /** 故障关联权重 5% */
    public static final double WEIGHT_FAULT_GRAPH = 0.05;

    // ============== 拍卖算法约束 ==============
    /** 终端可承接任务的最低电量阈值 */
    public static final float MIN_BATTERY_PCT = 20.0f;
    /** 单终端最大并发任务数 */
    public static final int MAX_TASK_PER_TERMINAL = 3;
    /** 应急/故障维修等重型任务的最低能力得分阈值，低于此值排除 */
    public static final double MIN_CAPABILITY_SCORE = 0.3;
    /** 知识/规程不匹配的竞拍价惩罚（终端类型与规程推荐不一致时加价） */
    public static final double KNOWLEDGE_PENALTY = 0.3;
    /** 拍卖竞拍价分量：距离系数 */
    public static final double BID_DISTANCE_COEF = 0.4;
    /** 拍卖竞拍价分量：能耗系数 */
    public static final double BID_ENERGY_COEF = 0.3;
    /** 拍卖竞拍价分量：能力匹配反向系数（匹配度越高减得越多） */
    public static final double BID_CAPABILITY_COEF = 0.2;
    /** 拍卖竞拍价分量：优先级反向系数（优先级越高减得越多） */
    public static final double BID_PRIORITY_COEF = 0.1;

    // ============== 数字孪生预演 ==============
    /** 多机最小安全间距（米） */
    public static final double SAFE_DISTANCE_M = 30.0;
    /** 预演时间步长（秒） */
    public static final double SIM_TIME_STEP_SEC = 0.5;
    /** 预演时序冲突容差（秒） */
    public static final double SIM_TIME_TOLERANCE_SEC = 5.0;

    // ============== 全局路径规划 ==============
    /** 默认巡航速度 m/s */
    public static final double CRUISE_SPEED_MPS = 8.0;
    /** 每公里耗电估算（%） */
    public static final double BATTERY_PCT_PER_KM = 12.0;
    /** RRT* 最大采样次数 */
    public static final int RRT_MAX_SAMPLES = 200;
    /** RRT* 单步扩展距离（米） */
    public static final double RRT_STEP_M = 50.0;

    // ============== 终端状态缓存 ==============
    /** 终端状态缓存 TTL（秒） */
    public static final int TERMINAL_STATE_TTL_SEC = 5;
    /** 终端状态键前缀 */
    public static final String TERMINAL_STATE_KEY_PREFIX = "raccoon:dispatch:terminal-state:";

    // ============== 全链路时延 ==============
    /** 端到端处理时延上限 ms */
    public static final long E2E_TIMEOUT_MS = 200L;
    /** 单场景终端容量上限 */
    public static final int MAX_TERMINALS_PER_SCENE = 100;
}
