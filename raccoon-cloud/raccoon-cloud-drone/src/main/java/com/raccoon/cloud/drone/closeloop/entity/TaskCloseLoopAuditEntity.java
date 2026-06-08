package com.raccoon.cloud.drone.closeloop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 闭环审计与合规导出（步骤7）。
 */
@Data
@TableName("task_close_loop_audit")
public class TaskCloseLoopAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;
    private Long workOrderId;
    private String taskName;

    /** COMPLETED / PARTIAL / MANUAL_REQUIRED / COLLECTING */
    private String closeStatus;
    private Double completionRate;

    /** 漏检明细 JSON */
    private String missedItemsJson;
    /** 报告内容 JSON */
    private String reportJson;
    private String reportSummary;

    /** 补检任务 ID（如有） */
    private String rescheduleTaskId;
    private Integer rescheduleCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closeTime;
}
