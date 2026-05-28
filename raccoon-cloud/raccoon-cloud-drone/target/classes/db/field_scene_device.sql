USE `hxsay_agent_drone`;

-- 现场设备扩展字段（与 uav_inspection_device 共用，供 NLP/路径规划/仿真同步）
-- 若列已存在请跳过对应 ALTER

ALTER TABLE `uav_inspection_device` ADD COLUMN `longitude` DOUBLE NULL COMMENT 'WGS84经度';
ALTER TABLE `uav_inspection_device` ADD COLUMN `latitude` DOUBLE NULL COMMENT 'WGS84纬度';
ALTER TABLE `uav_inspection_device` ADD COLUMN `height` DOUBLE NULL COMMENT 'WGS84高度米';
ALTER TABLE `uav_inspection_device` ADD COLUMN `scene_x` DOUBLE NULL;
ALTER TABLE `uav_inspection_device` ADD COLUMN `scene_y` DOUBLE NULL;
ALTER TABLE `uav_inspection_device` ADD COLUMN `scene_z` DOUBLE NULL;
ALTER TABLE `uav_inspection_device` ADD COLUMN `scene_type` VARCHAR(32) NULL;
ALTER TABLE `uav_inspection_device` ADD COLUMN `scene_object_id` VARCHAR(64) NULL;
ALTER TABLE `uav_inspection_device` ADD COLUMN `location_desc` VARCHAR(255) NULL;
ALTER TABLE `uav_inspection_device` ADD COLUMN `sync_source` VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE `uav_inspection_device` ADD COLUMN `remark` VARCHAR(255) NULL;

-- 初始化内置杆塔坐标请调用 API：POST /field-scene-device/init-builtin-towers
