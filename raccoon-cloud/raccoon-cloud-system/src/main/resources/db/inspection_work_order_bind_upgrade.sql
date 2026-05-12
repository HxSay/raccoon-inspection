-- 为已部署库补充：巡检工单与计划/任务绑定、任务反查工单
-- 若某列已存在会报 Duplicate column，可跳过对应 ALTER。

ALTER TABLE inspection_work_order
    ADD COLUMN plan_id BIGINT NULL COMMENT '来源巡检计划(inspection_plan.id)' AFTER update_by;

ALTER TABLE inspection_work_order
    ADD COLUMN task_id BIGINT NULL COMMENT '关联巡检任务(inspection_task.id)' AFTER plan_id;

CREATE INDEX idx_iwo_plan_id ON inspection_work_order (plan_id);
CREATE INDEX idx_iwo_task_id ON inspection_work_order (task_id);

ALTER TABLE inspection_task
    ADD COLUMN work_order_id BIGINT NULL COMMENT '关联巡检工单(inspection_work_order.id)' AFTER plan_id;

CREATE INDEX idx_inspection_task_work_order ON inspection_task (work_order_id);
