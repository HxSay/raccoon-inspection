-- 巡检管理 + 设备台账 + 维修工单（依据设计文档，表名小调整：sys_dept -> inspection_dept，warehouse -> inspection_warehouse）
-- 请在业务库执行（与 application.yml 中 datasource 一致）

CREATE TABLE IF NOT EXISTS inspection_dept (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    parent_id BIGINT NULL COMMENT '父部门ID',
    dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    leader_user_id BIGINT NULL COMMENT '部门负责人(um_user.id)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_leader_id (leader_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检模块-部门表';

CREATE TABLE IF NOT EXISTS inspection_warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
    warehouse_name VARCHAR(100) NOT NULL COMMENT '仓库名称',
    location VARCHAR(200) NULL COMMENT '仓库位置',
    manager_user_id BIGINT NULL COMMENT '仓库管理员(um_user.id)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检模块-仓库表';

CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
    supplier_name VARCHAR(100) NOT NULL COMMENT '供应商名称',
    contact_person VARCHAR(50) NULL COMMENT '联系人',
    contact_phone VARCHAR(20) NULL COMMENT '联系电话',
    address VARCHAR(200) NULL COMMENT '联系地址',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

CREATE TABLE IF NOT EXISTS device_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    parent_id BIGINT NULL COMMENT '父分类ID',
    category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    description VARCHAR(500) NULL COMMENT '分类描述',
    sort INT DEFAULT 0 COMMENT '排序权重',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分类表';

CREATE TABLE IF NOT EXISTS device_info (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备主表';

CREATE TABLE IF NOT EXISTS inspection_point (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '巡检点ID',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    point_name VARCHAR(100) NOT NULL COMMENT '巡检点名称',
    point_type TINYINT NOT NULL COMMENT '1手动 2自动采集',
    unit VARCHAR(20) NULL COMMENT '单位',
    min_threshold DECIMAL(10,2) NULL,
    max_threshold DECIMAL(10,2) NULL,
    standard_value DECIMAL(10,2) NULL,
    sort INT DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检点表';

CREATE TABLE IF NOT EXISTS inspection_plan (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '计划ID',
    plan_name VARCHAR(100) NOT NULL COMMENT '计划名称',
    device_ids TEXT NULL COMMENT '设备ID JSON数组',
    cycle_type TINYINT NOT NULL COMMENT '1天 2周 3月',
    cycle_value INT NOT NULL COMMENT '周期值',
    exec_user_id BIGINT NOT NULL COMMENT '执行人(um_user.id)',
    start_time DATETIME NOT NULL,
    end_time DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_exec_user (exec_user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检计划表';

CREATE TABLE IF NOT EXISTS inspection_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    task_code VARCHAR(50) NOT NULL COMMENT '任务单号',
    plan_id BIGINT NULL COMMENT '来源计划',
    work_order_id BIGINT NULL COMMENT '关联巡检工单(inspection_work_order.id)',
    device_id BIGINT NOT NULL,
    task_name VARCHAR(100) NOT NULL,
    exec_user_id BIGINT NOT NULL,
    plan_execute_time DATETIME NOT NULL,
    actual_execute_time DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2完成 3过期',
    is_abnormal TINYINT DEFAULT 0,
    longitude DECIMAL(10,6) NULL,
    latitude DECIMAL(10,6) NULL,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_code (task_code),
    KEY idx_device (device_id),
    KEY idx_exec (exec_user_id),
    KEY idx_status (status),
    KEY idx_plan_time (plan_execute_time),
    KEY idx_work_order (work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检任务表';

CREATE TABLE IF NOT EXISTS inspection_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    check_value VARCHAR(100) NULL,
    is_normal TINYINT NOT NULL DEFAULT 1,
    image_urls TEXT NULL COMMENT 'JSON 图片列表',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task (task_id),
    KEY idx_device (device_id),
    KEY idx_point (point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡检记录表';

CREATE TABLE IF NOT EXISTS work_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_code VARCHAR(50) NOT NULL,
    device_id BIGINT NOT NULL,
    source TINYINT NOT NULL COMMENT '1巡检 2手动 3预警',
    fault_description VARCHAR(500) NULL,
    fault_image_urls TEXT NULL,
    order_type TINYINT NOT NULL COMMENT '1维修 2保养',
    level TINYINT DEFAULT 2 COMMENT '1紧急2普通3低',
    assign_user_id BIGINT NULL,
    report_user_id BIGINT NOT NULL,
    report_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    plan_finish_time DATETIME NULL,
    actual_finish_time DATETIME NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理1处理中2待验收3完成4取消',
    repair_content VARCHAR(500) NULL,
    repair_cost DECIMAL(10,2) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_code (order_code),
    KEY idx_device (device_id),
    KEY idx_assign (assign_user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修工单表';
