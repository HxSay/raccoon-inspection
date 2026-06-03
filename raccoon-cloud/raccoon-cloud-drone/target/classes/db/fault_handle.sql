-- 故障事件表：边缘/终端/人工上报的标准化异常
CREATE TABLE IF NOT EXISTS `fault_event` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id`            VARCHAR(64)  NOT NULL COMMENT '事件UUID',
  `device_id`           BIGINT       NULL COMMENT '被巡检设备ID',
  `source_terminal_id`  BIGINT       NULL COMMENT '发现异常的终端ID',
  `map_id`              BIGINT       NULL COMMENT '场景地图ID',
  `fault_type`          VARCHAR(64)  NOT NULL COMMENT '故障类型编码',
  `fault_level`         VARCHAR(32)  NULL COMMENT '定级 GENERAL/SERIOUS/CRITICAL',
  `confidence`          DOUBLE       NULL COMMENT 'AI置信度0~1',
  `severity_score`      DOUBLE       NULL COMMENT '综合严重度评分',
  `description`         VARCHAR(1024) NULL COMMENT '异常描述',
  `evidence_url`        VARCHAR(512) NULL COMMENT '证据文件URL',
  `ext_data_json`       TEXT         NULL COMMENT '扩展字段JSON',
  `detect_time`         DATETIME     NOT NULL COMMENT '发现时间',
  `merged`              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否被5分钟去重合并',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_device_type_time` (`device_id`, `fault_type`, `detect_time`),
  KEY `idx_map_id` (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障事件表';

-- 故障分级处置审计表
CREATE TABLE IF NOT EXISTS `fault_handle_audit` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id`              VARCHAR(64)  NOT NULL COMMENT '关联故障事件ID',
  `fault_level`           VARCHAR(32)  NULL COMMENT '定级结果',
  `response_action`       VARCHAR(64)  NULL COMMENT '处置动作',
  `expand_scope_json`     TEXT         NULL COMMENT '扩大范围明细JSON',
  `assigned_terminal_ids` VARCHAR(256) NULL COMMENT '分配终端ID列表',
  `reinspect_task_id`     VARCHAR(64)  NULL COMMENT '复巡调度任务ID',
  `cited_regulation_ids`  TEXT         NULL COMMENT 'Milvus规程引用JSON',
  `graph_impact_summary`  TEXT         NULL COMMENT 'Neo4j影响面摘要',
  `handle_time`           DATETIME     NOT NULL COMMENT '处置时间',
  `handle_result`         VARCHAR(32)  NULL COMMENT 'SUCCESS/PARTIAL/FAILED/MERGED',
  `detail_json`           MEDIUMTEXT   NULL COMMENT '完整处置结果JSON',
  PRIMARY KEY (`id`),
  KEY `idx_event_id` (`event_id`),
  KEY `idx_handle_time` (`handle_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障分级处置审计表';
