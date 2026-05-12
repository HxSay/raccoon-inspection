-- 巡检工单：device、inspection_work_order、inspection_work_order_detail
-- 在业务库执行（与 application.yml datasource 一致）
-- 若已有 device_info，可执行末尾 INSERT IGNORE 将台账同步到 device 表（保持 id 一致便于关联）

CREATE TABLE IF NOT EXISTS device (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备唯一ID',
    device_code VARCHAR(50) NOT NULL COMMENT '设备编号',
    device_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    model VARCHAR(50) NULL COMMENT '设备型号',
    serial_number VARCHAR(50) NULL COMMENT '出厂序列号',
    category_id BIGINT NULL COMMENT '设备分类ID',
    dept_id BIGINT NULL COMMENT '所属部门(inspection_dept.id)',
    manager_user_id BIGINT NULL COMMENT '设备责任人(um_user.id)',
    supplier_id BIGINT NULL COMMENT '供应商ID',
    location VARCHAR(200) NULL COMMENT '安装位置',
    longitude DECIMAL(10,6) NULL COMMENT '经度',
    latitude DECIMAL(10,6) NULL COMMENT '纬度',
    qrcode_url VARCHAR(255) NULL COMMENT '二维码图片地址',
    purchase_date DATE NULL COMMENT '采购日期',
    activate_date DATE NULL COMMENT '启用日期',
    warranty_end_date DATE NULL COMMENT '保修截止日期',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 2待维护 3故障 4闲置 5报废',
    inspection_cycle INT NULL COMMENT '巡检周期(天)',
    maintenance_cycle INT NULL COMMENT '保养周期(天)',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_code (device_code),
    KEY idx_serial_number (serial_number),
    KEY idx_category_id (category_id),
    KEY idx_dept_id (dept_id),
    KEY idx_manager_id (manager_user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

CREATE TABLE IF NOT EXISTS inspection_work_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no VARCHAR(64) NOT NULL COMMENT '工单编号',
    area VARCHAR(100) NOT NULL COMMENT '巡检区域',
    shift_type TINYINT NOT NULL COMMENT '班次 1早班 2中班 3夜班',
    inspector_id BIGINT NOT NULL COMMENT '巡检人员ID(um_user.id)',
    inspector_name VARCHAR(64) NULL COMMENT '巡检人员姓名',
    plan_start_time DATETIME NOT NULL COMMENT '计划开始时间',
    plan_end_time DATETIME NOT NULL COMMENT '计划结束时间',
    actual_start_time DATETIME NULL COMMENT '实际开始时间',
    actual_end_time DATETIME NULL COMMENT '实际结束时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1待下发 2待执行 3执行中 4已完成 5已取消',
    create_by BIGINT NOT NULL COMMENT '创建人ID',
    create_by_name VARCHAR(64) NULL COMMENT '创建人姓名',
    update_by BIGINT NULL COMMENT '更新人ID',
    plan_id BIGINT NULL COMMENT '来源巡检计划(inspection_plan.id)',
    task_id BIGINT NULL COMMENT '关联巡检任务(inspection_task.id)',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_area (area),
    KEY idx_shift_type (shift_type),
    KEY idx_status (status),
    KEY idx_inspector_id (inspector_id),
    KEY idx_plan_start_time (plan_start_time),
    KEY idx_plan_id (plan_id),
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检工单主表';

CREATE TABLE IF NOT EXISTS inspection_work_order_detail (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT NOT NULL COMMENT '工单ID',
    step_order INT NOT NULL COMMENT '步骤序号',
    step_type VARCHAR(20) NOT NULL COMMENT 'path/stop/collect/report',
    target VARCHAR(200) NULL COMMENT '目标位置或路径描述',
    description VARCHAR(500) NULL COMMENT '步骤说明',
    device_id BIGINT NULL COMMENT '关联设备(device.id)',
    device_name VARCHAR(100) NULL COMMENT '设备名称快照',
    check_item VARCHAR(200) NULL COMMENT '检测项名称',
    standard_min DECIMAL(12,4) NULL COMMENT '标准下限',
    standard_max DECIMAL(12,4) NULL COMMENT '标准上限',
    unit VARCHAR(20) NULL COMMENT '单位',
    actual_value VARCHAR(200) NULL COMMENT '实测值',
    is_exception TINYINT NOT NULL DEFAULT 0 COMMENT '是否异常 0否 1是',
    collect_time DATETIME NULL COMMENT '采集或步骤完成时间',
    photo_url VARCHAR(1000) NULL COMMENT '现场照片URL',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    UNIQUE KEY uk_order_step (order_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检工单详情步骤表';

-- 从 device_info 同步到 device（id 对齐，便于工单详情关联设备编号等）
INSERT IGNORE INTO device (
    id, device_code, device_name, model, serial_number, category_id, dept_id, manager_user_id, supplier_id,
    location, longitude, latitude, qrcode_url, purchase_date, activate_date, warranty_end_date,
    status, inspection_cycle, maintenance_cycle, remark, create_time, update_time
)
SELECT
    id, device_code, device_name, model, serial_number, category_id, dept_id, manager_user_id, supplier_id,
    location, longitude, latitude, qrcode_url, purchase_date, activate_date, warranty_end_date,
    status, inspection_cycle, maintenance_cycle, remark, create_time, update_time
FROM device_info;

-- 若库中 inspection_work_order / inspection_task 为旧结构，请执行 inspection_work_order_bind_upgrade.sql 增加绑定字段。
