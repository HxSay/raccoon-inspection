/** 输电巡检场景杆塔排布（与 scene.ts createPowerlineScene 一致） */
export const PATROL_TOWER_XS = [-95, -15, 65, 145, 225] as const
export const PATROL_TOWER_HEIGHTS = [58, 64, 60, 62, 59] as const
export const PATROL_CORRIDOR_Z0 = -35

/** 机巢 / 起飞锚点（世界坐标，Y 为地面高度） */
export const PATROL_NEST_HOME = { x: 0, y: 3, z: 40 } as const

/** 边缘无人机地面站（就地控制终端） */
export const PATROL_GROUND_STATION = { x: -42, y: 0, z: 72 } as const
