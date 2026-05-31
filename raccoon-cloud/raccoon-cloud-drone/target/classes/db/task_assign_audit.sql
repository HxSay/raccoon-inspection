-- 任务分配审计表：记录中央 Agent 每次拍卖分配的决策、知识引用与竞拍明细（可解释/复盘）
CREATE TABLE IF NOT EXISTS `task_assign_audit` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id`          VARCHAR(64)  NOT NULL COMMENT '调度任务ID',
  `request_id`       VARCHAR(64)  NULL COMMENT '外部请求ID',
  `map_id`           BIGINT       NULL COMMENT '场景地图ID',
  `terminal_id`      BIGINT       NULL COMMENT '中标终端ID',
  `terminal_name`    VARCHAR(128) NULL COMMENT '中标终端名称',
  `assigned`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否分配成功',
  `priority`         VARCHAR(32)  NULL COMMENT '任务优先级档位',
  `priority_score`   DOUBLE       NULL COMMENT '知识增强优先级综合得分',
  `bid_price`        DOUBLE       NULL COMMENT '中标竞拍价',
  `assign_reason`    VARCHAR(1024) NULL COMMENT '分配理由（可解释摘要）',
  `required_sensors` VARCHAR(256) NULL COMMENT '任务要求传感器（逗号分隔）',
  `cited_chunk_ids`  TEXT         NULL COMMENT '引用的Milvus chunk ID列表(JSON)',
  `cited_graph_refs` TEXT         NULL COMMENT '引用的Neo4j关系摘要(JSON)',
  `bid_matrix`       MEDIUMTEXT   NULL COMMENT '竞拍价矩阵明细(JSON)',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_map_id` (`map_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务分配审计表';
