USE `hxsay_agent_drone`;

-- 扩展 uav_info 为通用巡检机器人台账（若列已存在请跳过对应语句）
ALTER TABLE `uav_info` ADD COLUMN `robot_type` VARCHAR(32) NOT NULL DEFAULT 'UAV' COMMENT '机器人类型' AFTER `uav_code`;
ALTER TABLE `uav_info` ADD COLUMN `marker_label` VARCHAR(32) NULL COMMENT '场景标记' AFTER `robot_type`;
ALTER TABLE `uav_info` ADD COLUMN `marker_color` VARCHAR(16) NULL DEFAULT '#409EFF' AFTER `marker_label`;
ALTER TABLE `uav_info` ADD COLUMN `scene_x` DOUBLE NULL AFTER `marker_color`;
ALTER TABLE `uav_info` ADD COLUMN `scene_y` DOUBLE NULL AFTER `scene_x`;
ALTER TABLE `uav_info` ADD COLUMN `scene_z` DOUBLE NULL AFTER `scene_y`;
ALTER TABLE `uav_info` ADD COLUMN `remark` VARCHAR(255) NULL AFTER `scene_z`;

UPDATE `uav_info` SET `robot_type`='UAV', `marker_label`='UAV-01', `marker_color`='#409EFF', `scene_x`=0, `scene_y`=3, `scene_z`=40 WHERE `id`=1;
UPDATE `uav_info` SET `robot_type`='UAV', `marker_label`='UAV-02', `marker_color`='#67C23A', `scene_x`=0, `scene_y`=3, `scene_z`=80 WHERE `id`=2;
UPDATE `uav_info` SET `robot_type`='UAV', `marker_label`='UAV-101', `marker_color`='#E6A23C', `scene_x`=-20, `scene_y`=0, `scene_z`=0 WHERE `id`=3;

INSERT INTO `uav_info` (`uav_name`, `uav_code`, `robot_type`, `map_id`, `marker_label`, `marker_color`, `scene_x`, `scene_y`, `scene_z`, `status`, `remark`) VALUES
  ('火电站机器狗-01', 'DOG-001', 'ROBOT_DOG', 3, 'DOG-01', '#F56C6C', 0, 0, 0, 1, '热力管网廊道巡检'),
  ('变电站轮式机器人-01', 'GR-001', 'GROUND_ROBOT', 2, 'GR-01', '#909399', 10, 0, 5, 1, '站内设备巡检');
