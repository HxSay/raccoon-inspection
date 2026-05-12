package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检工单主表 inspection_work_order。
 */
@Data
@TableName("inspection_work_order")
public class InspectionWorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 工单编号 */
    private String orderNo;
    /** 巡检区域 */
    private String area;
    /** 班次 1早班 2中班 3夜班 */
    private Integer shiftType;
    private Long inspectorId;
    private String inspectorName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planStartTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;
    /**
     * 1待下发 2待执行 3执行中 4已完成 5已取消
     */
    private Integer status;
    private Long createBy;
    private String createByName;
    private Long updateBy;
    /** 来源巡检计划 */
    private Long planId;
    /** 关联巡检任务 */
    private Long taskId;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
