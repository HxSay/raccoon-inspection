/**
 * 与边缘端大疆自主巡检业务对齐的仿真常量（便于与真机/后端 JSON 对照）。
 * 若真机侧参数调整，请优先在此集中修改。
 */

/** 云端下发航线接口的固定网络延迟（毫秒），不含部署模式附加延迟 */
export const CLOUD_MISSION_FETCH_DELAY_MS = 200

/** 最大飞行速度（m/s），对应大疆任务全局上限 */
export const MAX_FLIGHT_SPEED = 15

/** 自动航线巡航速度（m/s） */
export const AUTO_FLIGHT_SPEED = 8

/** 航点/巡检点接近时减速目标速度（m/s） */
export const INSPECTION_POINT_SPEED = 5

/** 航向模式：AUTO（沿航线机头朝向） */
export const HEADING_MODE_AUTO = 'AUTO' as const

/** 任务完成后自动返航 */
export const FINISH_ACTION_AUTO_RTH = true

/** 拍照点云台俯仰角（度，向下为负） */
export const PHOTO_GIMBAL_PITCH_DEG = -30

/** 电量低于该百分比禁止起飞（与边缘端校验一致） */
export const BATTERY_MIN_TAKEOFF_PERCENT = 20

/** RTK 固定解模式值（mode === 2 才允许高精度巡检） */
export const RTK_MODE_FIXED = 2

/** 错误文案：与飞控/边缘端提示保持一致便于联调 */
export const ERR_BATTERY_LOW = '电量不足，无法起飞!'
export const ERR_RTK_NOT_FIXED = '未切换到RTK定位，无法执行高精度巡检!'

/** 大疆 M300 系列航点任务上限（长距离巡检分航段时需关注） */
export const DJI_MAX_WAYPOINTS = 65535

/** 状态上报频率：10Hz */
export const TELEMETRY_INTERVAL_MS = 100

/** 部署模式：附加到云端请求的往返延迟（毫秒） */
export const DEPLOY_EXTRA_LATENCY_MS = {
  /** 地面站：链路较长 */
  groundStation: 100,
  /** 机载算力：本地回环更短 */
  onboard: 20
} as const

/** 场景坐标到“伪 GPS”的换算系数（仅仿真展示） */
export const SCENE_TO_LAT_LON = { latPerMeter: 1 / 111320, lonPerMeter: 1 / (111320 * Math.cos((30 * Math.PI) / 180)) }
