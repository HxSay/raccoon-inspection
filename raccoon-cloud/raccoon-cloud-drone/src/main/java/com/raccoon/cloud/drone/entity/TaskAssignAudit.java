package com.raccoon.cloud.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务分配审计记录。
 * <p>每次拍卖分配完成后落库分配决策、引用的 Milvus chunk、Neo4j 关联关系与竞拍明细，
 * 供工单审核与运维复盘使用（对应优化方案步骤 6 可解释性与审计）。
 *
 * @author raccoon
 */
@Data
@TableName("task_assign_audit")
public class TaskAssignAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调度任务 ID */
    private String taskId;

    /** 外部请求 ID */
    private String requestId;

    /** 场景地图 ID */
    private Long mapId;

    /** 中标终端 ID（未分配为空） */
    private Long terminalId;

    /** 中标终端名称 */
    private String terminalName;

    /** 是否分配成功 */
    private Boolean assigned;

    /** 任务优先级档位 */
    private String priority;

    /** 知识增强优先级综合得分 */
    private Double priorityScore;

    /** 中标竞拍价 */
    private Double bidPrice;

    /** 分配理由（可解释摘要） */
    private String assignReason;

    /** 任务要求传感器（逗号分隔） */
    private String requiredSensors;

    /** 引用的 Milvus chunk ID 列表（JSON） */
    private String citedChunkIds;

    /** 引用的 Neo4j 关系摘要（JSON） */
    private String citedGraphRefs;

    /** 竞拍价矩阵明细（JSON） */
    private String bidMatrix;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
