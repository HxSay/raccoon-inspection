-- 无人机巡检路径规划表（库名与 application.yml 中一致）
CREATE DATABASE IF NOT EXISTS `hxsay_agent_drone` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `hxsay_agent_drone`;

CREATE TABLE IF NOT EXISTS `uav_route_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` BIGINT NULL COMMENT '关联的巡检任务ID',
  `map_id` BIGINT NOT NULL COMMENT '所属地图ID',
  `uav_id` BIGINT NOT NULL COMMENT '分配的无人机ID',
  `start_point` VARCHAR(100) NOT NULL COMMENT '起飞点坐标（经纬度,高度）',
  `end_point` VARCHAR(100) NOT NULL COMMENT '降落点坐标（经纬度,高度）',
  `total_distance` DOUBLE NOT NULL COMMENT '总飞行距离（米）',
  `estimated_time` INT NOT NULL COMMENT '预计耗时（秒）',
  `estimated_battery` FLOAT NOT NULL COMMENT '预计消耗电量（%）',
  `algorithm` VARCHAR(20) NOT NULL COMMENT '使用的算法：A*, RRT*',
  `path_points` TEXT NOT NULL COMMENT '路径点列表（JSON数组，经纬度,高度）',
  `photo_points` TEXT NOT NULL COMMENT '拍照航点列表（JSON数组，含 deviceIds 绑定设备）',
  `visit_order` TEXT NOT NULL COMMENT '巡检点访问顺序（JSON数组，设备ID）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_task_id` (`task_id`),
  INDEX `idx_uav_id` (`uav_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无人机巡检路径规划表';

-- 下拉选项数据表（亦可单独执行 db/uav_map_uav_info.sql）
CREATE TABLE IF NOT EXISTS `uav_map` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '地图ID',
  `map_name` VARCHAR(100) NOT NULL COMMENT '地图名称',
  `scene_type` VARCHAR(32) NULL COMMENT '场景类型',
  `remark` VARCHAR(255) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无人机仿真地图';

CREATE TABLE IF NOT EXISTS `uav_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '无人机ID',
  `uav_name` VARCHAR(100) NOT NULL COMMENT '无人机名称',
  `uav_code` VARCHAR(64) NULL,
  `map_id` BIGINT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_map_id` (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无人机台账';

INSERT INTO `uav_map` (`id`, `map_name`, `scene_type`, `remark`) VALUES
  (1, '输电线路巡检场景', 'patrol', '默认巡检地图'),
  (2, '变电站场景', 'substation', NULL),
  (3, '热力管网场景', 'thermal', NULL)
ON DUPLICATE KEY UPDATE `map_name` = VALUES(`map_name`);

INSERT INTO `uav_info` (`id`, `uav_name`, `uav_code`, `map_id`, `status`) VALUES
  (1, '巡检无人机-01', 'UAV-001', 1, 1),
  (2, '巡检无人机-02', 'UAV-002', 1, 1),
  (3, '变电站无人机-01', 'UAV-101', 2, 1)
ON DUPLICATE KEY UPDATE `uav_name` = VALUES(`uav_name`);
