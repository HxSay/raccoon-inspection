-- 任务闭环审计表：任务规划 Agent 巡检任务闭环（结果回收→核对→报告→归档→重调度→工单闭环→审计）
CREATE TABLE IF NOT EXISTS `task_close_loop_audit` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`           VARCHAR(64)  NOT NULL COMMENT '调度任务ID',
  `work_order_id`     BIGINT       NULL COMMENT '关联CMMS工单ID',
  `task_name`         VARCHAR(255) NULL COMMENT '任务名称',
  `close_status`      VARCHAR(32)  NOT NULL COMMENT 'COMPLETED/PARTIAL/MANUAL_REQUIRED/COLLECTING',
  `completion_rate`   DOUBLE       NULL COMMENT '综合完成率0~1',
  `missed_items_json` MEDIUMTEXT   NULL COMMENT '漏检明细JSON（设备/航点/数据缺口）',
  `report_json`       MEDIUMTEXT   NULL COMMENT '巡检报告内容JSON',
  `report_summary`    VARCHAR(1024) NULL COMMENT '报告执行摘要',
  `reschedule_task_id` VARCHAR(64) NULL COMMENT '补检任务ID（如有）',
  `reschedule_count`  INT          NULL DEFAULT 0 COMMENT '补检轮次',
  `close_time`        DATETIME     NOT NULL COMMENT '闭环时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_close_time` (`close_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务闭环审计表';
