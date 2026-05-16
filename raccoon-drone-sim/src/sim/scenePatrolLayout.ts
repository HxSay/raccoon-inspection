/** 机巢 / 起飞锚点（世界坐标，Y 为地面高度；X 与线路中间塔对齐） */
export const PATROL_NEST_HOME = { x: 0, y: 3, z: 40 } as const

/** 特高压相邻杆塔档距（米） */
export const PATROL_TOWER_SPAN_M = 1000

/** 输电巡检场景杆塔排布（与 scene.ts createPowerlineScene 一致） */
export const PATROL_TOWER_XS = [
  PATROL_NEST_HOME.x - 2 * PATROL_TOWER_SPAN_M,
  PATROL_NEST_HOME.x - PATROL_TOWER_SPAN_M,
  PATROL_NEST_HOME.x,
  PATROL_NEST_HOME.x + PATROL_TOWER_SPAN_M,
  PATROL_NEST_HOME.x + 2 * PATROL_TOWER_SPAN_M
] as const

/** 特高压构架塔全高（米，沿线路略有差异） */
export const PATROL_TOWER_HEIGHTS = [96, 102, 98, 100, 97] as const
export const PATROL_CORRIDOR_Z0 = -35

/** 边缘无人机地面站（就地控制终端） */
export const PATROL_GROUND_STATION = { x: -42, y: 0, z: 72 } as const

/** 鸟瞰默认相机（覆盖约 4 km 线路） */
export const PATROL_AERIAL_CAMERA = { x: 520, y: 155, z: 360 } as const

/** 线路走廊观察目标点 */
export const PATROL_SCENE_LOOK = { x: 0, y: 48, z: PATROL_CORRIDOR_Z0 } as const
