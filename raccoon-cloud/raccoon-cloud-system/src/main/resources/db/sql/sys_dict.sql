-- 字典类型表
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dict_type_code` varchar(64) NOT NULL COMMENT '字典类型编码（唯一标识，如user_status、order_status）',
  `dict_type_name` varchar(128) NOT NULL COMMENT '字典类型名称（如用户状态、订单状态）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0=禁用，1=启用',
  `remark` varchar(512) DEFAULT '' COMMENT '备注',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `create_by` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人账号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人账号',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code_tenant` (`dict_type_code`,`tenant_id`,`is_deleted`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status_is_deleted` (`status`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 字典数据表
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dict_type_code` varchar(64) NOT NULL COMMENT '字典类型编码',
  `dict_label` varchar(128) NOT NULL COMMENT '字典显示名称',
  `dict_value` varchar(64) NOT NULL COMMENT '字典存储值',
  `dict_extend` varchar(1024) DEFAULT '' COMMENT '扩展字段',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0=禁用，1=启用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `remark` varchar(512) DEFAULT '' COMMENT '备注',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `create_by` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人账号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人账号',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_value_tenant` (`dict_type_code`,`dict_value`,`tenant_id`,`is_deleted`),
  UNIQUE KEY `uk_dict_type_label_tenant` (`dict_type_code`,`dict_label`,`tenant_id`,`is_deleted`),
  KEY `idx_dict_type_code` (`dict_type_code`,`status`,`is_deleted`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

-- 初始化字典数据
INSERT INTO `sys_dict_type` (`dict_type_code`, `dict_type_name`, `status`, `remark`, `sort`, `tenant_id`, `create_by`, `update_by`) VALUES
('user_status', '用户状态', 1, '系统用户状态字典', 1, 0, 'admin', 'admin'),
('gender', '性别', 1, '用户性别字典', 2, 0, 'admin', 'admin'),
('order_status', '订单状态', 1, '订单状态字典', 3, 0, 'admin', 'admin');

INSERT INTO `sys_dict_data` (`dict_type_code`, `dict_label`, `dict_value`, `dict_extend`, `status`, `sort`, `tenant_id`, `create_by`, `update_by`) VALUES
('user_status', '正常', '1', '{"color":"#52c41a"}', 1, 1, 0, 'admin', 'admin'),
('user_status', '禁用', '0', '{"color":"#ff4d4f"}', 1, 2, 0, 'admin', 'admin'),
('gender', '男', '1', '{"icon":"male"}', 1, 1, 0, 'admin', 'admin'),
('gender', '女', '2', '{"icon":"female"}', 1, 2, 0, 'admin', 'admin'),
('gender', '未知', '0', '{"icon":"unknown"}', 1, 0, 0, 'admin', 'admin'),
('order_status', '待支付', 'pending', '{"color":"#faad14"}', 1, 1, 0, 'admin', 'admin'),
('order_status', '已支付', 'paid', '{"color":"#52c41a"}', 1, 2, 0, 'admin', 'admin'),
('order_status', '已取消', 'cancelled', '{"color":"#ff4d4f"}', 1, 3, 0, 'admin', 'admin'),
('order_status', '已完成', 'completed', '{"color":"#1890ff"}', 1, 4, 0, 'admin', 'admin');
