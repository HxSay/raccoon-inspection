-- 若曾创建过 task_id 唯一索引导致重复提交失败，可执行本脚本
USE `hxsay_agent_drone`;
ALTER TABLE `uav_route_plan` DROP INDEX `task_id`;
