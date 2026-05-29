USE `hxsay_agent_drone`;

CREATE TABLE IF NOT EXISTS `uav_inspection_device` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '巡检设备ID',
  `map_id` BIGINT NOT NULL COMMENT '所属地图/区域',
  `device_name` VARCHAR(100) NOT NULL COMMENT '设备名称',
  `device_type` VARCHAR(64) NULL COMMENT '设备类型',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 0停用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_map_device` (`map_id`, `device_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检目标设备（LLM槽位校验）';

INSERT INTO `uav_inspection_device` (`id`, `map_id`, `device_name`, `device_type`) VALUES
  (1, 1, '杆塔1', 'tower'),
  (2, 1, '杆塔2', 'tower'),
  (3, 1, '杆塔3', 'tower'),
  (11, 1, '杆塔4', 'tower'),
  (12, 1, '杆塔5', 'tower'),
  (4, 1, '主变压器', 'transformer'),
  (5, 1, '断路器', 'breaker'),
  (6, 2, '主变压器', 'transformer'),
  (7, 2, '断路器', 'breaker'),
  (8, 2, '隔离开关', 'isolator'),
  (9, 3, '阀门', 'valve'),
  (10, 3, '换热站', 'station')
ON DUPLICATE KEY UPDATE `device_name` = VALUES(`device_name`);
