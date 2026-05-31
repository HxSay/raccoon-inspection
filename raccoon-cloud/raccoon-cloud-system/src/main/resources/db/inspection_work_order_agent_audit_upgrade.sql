-- 任务规划 Agent 工单审核扩展字段（重复执行请忽略 Duplicate column 错误）
ALTER TABLE inspection_work_order
    ADD COLUMN dispatch_task_id VARCHAR(64) NULL COMMENT 'drone调度任务ID' AFTER task_id;
ALTER TABLE inspection_work_order
    ADD COLUMN reject_reason VARCHAR(512) NULL COMMENT '审核驳回原因' AFTER remark;
ALTER TABLE inspection_work_order
    ADD COLUMN audit_deadline DATETIME NULL COMMENT '审核截止时间' AFTER reject_reason;
ALTER TABLE inspection_work_order
    ADD COLUMN priority_code VARCHAR(16) NULL COMMENT 'NORMAL/HIGH/URGENT' AFTER audit_deadline;
ALTER TABLE inspection_work_order
    ADD COLUMN terminal_id BIGINT NULL COMMENT '分配终端ID' AFTER priority_code;
ALTER TABLE inspection_work_order
    ADD COLUMN terminal_name VARCHAR(128) NULL COMMENT '分配终端名称' AFTER terminal_id;
ALTER TABLE inspection_work_order
    ADD COLUMN assign_reason VARCHAR(1024) NULL COMMENT '分配理由' AFTER terminal_name;
ALTER TABLE inspection_work_order
    ADD COLUMN path_plan_json MEDIUMTEXT NULL COMMENT '路径规划JSON' AFTER assign_reason;
ALTER TABLE inspection_work_order
    ADD COLUMN planning_payload_json MEDIUMTEXT NULL COMMENT '驳回重规划原始请求JSON' AFTER path_plan_json;
