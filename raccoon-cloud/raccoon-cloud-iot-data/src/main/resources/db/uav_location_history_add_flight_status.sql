-- 已有库升级：增加飞行状态字段
USE `hxsay_agent_iot`;
ALTER TABLE `uav_location_history`
  ADD COLUMN `flight_status` VARCHAR(24) NULL COMMENT '飞行状态：idle/mission/rth/done 等' AFTER `location_mode`;
