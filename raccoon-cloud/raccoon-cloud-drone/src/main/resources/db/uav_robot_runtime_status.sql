USE `hxsay_agent_drone`;

-- 静态作业范围（配置在机器人台账）
ALTER TABLE `uav_info` ADD COLUMN `work_range_desc` VARCHAR(512) NULL COMMENT '作业范围描述(静态)';

UPDATE `uav_info` SET `work_range_desc` = '输电线路巡检场景走廊，档距约200m×5基杆塔' WHERE `map_id` = 1 AND `id` IN (1, 2);
UPDATE `uav_info` SET `work_range_desc` = '变电站室内设备区' WHERE `map_id` = 2;
UPDATE `uav_info` SET `work_range_desc` = '火电站热力管网廊道' WHERE `map_id` = 3;

CREATE TABLE IF NOT EXISTS `uav_robot_runtime_status` (
  `uav_id` BIGINT NOT NULL COMMENT '机器人ID=uav_info.id',
  `longitude` DOUBLE NULL COMMENT '位置-经度 10Hz',
  `latitude` DOUBLE NULL COMMENT '位置-纬度',
  `height` DOUBLE NULL COMMENT '位置-高度m',
  `battery_pct` FLOAT NULL COMMENT '剩余电量% 1Hz',
  `endurance_min` INT NULL COMMENT '预计剩余续航分钟 1Hz',
  `assigned_task_count` INT NULL DEFAULT 0 COMMENT '已分配任务数 1Hz',
  `cpu_pct` FLOAT NULL COMMENT 'CPU负载% 1Hz',
  `memory_pct` FLOAT NULL COMMENT '内存负载% 1Hz',
  `online_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '1在线 0离线 10Hz',
  `flight_status` VARCHAR(32) NULL COMMENT 'STANDBY/FLYING/RTH/LANDING 10Hz',
  `fault_status` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/WARNING/FAULT',
  `fault_message` VARCHAR(255) NULL,
  `position_at` DATETIME NULL,
  `battery_at` DATETIME NULL,
  `load_at` DATETIME NULL,
  `runtime_at` DATETIME NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`uav_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检机器人实时状态快照(供Agent决策)';
