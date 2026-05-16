USE `hxsay_agent_iot`;

/** 边缘巡检任务会话（一次任务完成对应一条） */
CREATE TABLE IF NOT EXISTS `uav_inspection_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `uav_id` BIGINT NOT NULL COMMENT '无人机ID',
  `task_id` BIGINT NULL COMMENT '巡检任务ID',
  `plan_id` BIGINT NULL COMMENT '路径规划ID',
  `map_id` BIGINT NULL COMMENT '地图ID',
  `started_at` DATETIME NOT NULL COMMENT '任务开始时间',
  `finished_at` DATETIME NOT NULL COMMENT '任务结束时间',
  `distance_m` FLOAT NULL COMMENT '飞行距离米',
  `sample_count` INT NOT NULL DEFAULT 0 COMMENT '多模态采样条数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_uav_finished` (`uav_id`, `finished_at`),
  INDEX `idx_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无人机边缘巡检会话';

/** 多模态巡检采样（可见光/热成像/声音/振动/温度等） */
CREATE TABLE IF NOT EXISTS `uav_inspection_sample` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` BIGINT NOT NULL COMMENT '会话ID',
  `waypoint_index` INT NOT NULL COMMENT '航点序号',
  `modality_type` VARCHAR(24) NOT NULL COMMENT 'VISIBLE/THERMAL/AUDIO/VIBRATION/TEMPERATURE',
  `captured_at` DATETIME NOT NULL COMMENT '采样时刻',
  `longitude` DECIMAL(10,6) NULL,
  `latitude` DECIMAL(10,6) NULL,
  `height` FLOAT NULL,
  `payload_json` JSON NOT NULL COMMENT '模态载荷（指标或缩略图等）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_session` (`session_id`),
  INDEX `idx_session_wp` (`session_id`, `waypoint_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无人机多模态巡检采样';
