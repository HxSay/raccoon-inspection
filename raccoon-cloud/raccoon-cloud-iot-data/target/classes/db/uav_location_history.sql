CREATE DATABASE IF NOT EXISTS `hxsay_agent_iot` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `hxsay_agent_iot`;

CREATE TABLE IF NOT EXISTS `uav_location_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `uav_id` BIGINT NOT NULL COMMENT '无人机ID',
  `task_id` BIGINT NULL COMMENT '关联巡检任务ID',
  `map_id` BIGINT NULL COMMENT '所属地图ID',
  `longitude` DECIMAL(10,6) NOT NULL COMMENT '经度',
  `latitude` DECIMAL(10,6) NOT NULL COMMENT '纬度',
  `height` FLOAT NOT NULL COMMENT '飞行高度（米）',
  `speed` FLOAT NULL COMMENT '飞行速度（m/s）',
  `battery` FLOAT NULL COMMENT '剩余电量（%）',
  `location_mode` TINYINT NOT NULL COMMENT '定位模式（如 RTK 固定解=2）',
  `flight_status` VARCHAR(24) NULL COMMENT '飞行状态：idle/mission/rth/done 等',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
  PRIMARY KEY (`id`, `create_time`),
  INDEX `idx_uav_time` (`uav_id`, `create_time`),
  INDEX `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无人机历史轨迹表(按月份分区)'
PARTITION BY RANGE (TO_DAYS(`create_time`)) (
  PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
  PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
  PARTITION pmax VALUES LESS THAN (MAXVALUE)
);
