-- 无人机巡检路径规划表（库名与 application.yml 中一致）
CREATE DATABASE IF NOT EXISTS `hxsay_agent_drone` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `hxsay_agent_drone`;

CREATE TABLE IF NOT EXISTS `uav_route_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` BIGINT UNIQUE NULL COMMENT '关联的巡检任务ID',
  `map_id` BIGINT NOT NULL COMMENT '所属地图ID',
  `uav_id` BIGINT NOT NULL COMMENT '分配的无人机ID',
  `start_point` VARCHAR(100) NOT NULL COMMENT '起飞点坐标（经纬度,高度）',
  `end_point` VARCHAR(100) NOT NULL COMMENT '降落点坐标（经纬度,高度）',
  `total_distance` DOUBLE NOT NULL COMMENT '总飞行距离（米）',
  `estimated_time` INT NOT NULL COMMENT '预计耗时（秒）',
  `estimated_battery` FLOAT NOT NULL COMMENT '预计消耗电量（%）',
  `algorithm` VARCHAR(20) NOT NULL COMMENT '使用的算法：A*, RRT*',
  `path_points` TEXT NOT NULL COMMENT '路径点列表（JSON数组，经纬度,高度）',
  `photo_points` TEXT NOT NULL COMMENT '拍照航点列表（JSON数组）',
  `visit_order` TEXT NOT NULL COMMENT '巡检点访问顺序（JSON数组，设备ID）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_task_id` (`task_id`),
  INDEX `idx_uav_id` (`uav_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无人机巡检路径规划表';
