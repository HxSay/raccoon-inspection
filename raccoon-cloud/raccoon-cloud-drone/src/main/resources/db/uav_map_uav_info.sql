USE `hxsay_agent_drone`;

CREATE TABLE IF NOT EXISTS `uav_map` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '地图ID',
  `map_name` VARCHAR(100) NOT NULL COMMENT '地图名称',
  `scene_type` VARCHAR(32) NULL COMMENT '场景类型：patrol/substation/thermal',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无人机仿真地图';

CREATE TABLE IF NOT EXISTS `uav_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '无人机ID',
  `uav_name` VARCHAR(100) NOT NULL COMMENT '无人机名称',
  `uav_code` VARCHAR(64) NULL COMMENT '编号',
  `map_id` BIGINT NOT NULL COMMENT '所属地图',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1可用 0停用',
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
