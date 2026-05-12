package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inspection_task")
public class InspectionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private Long planId;
    private Long deviceId;
    private String taskName;
    private Long execUserId;
    private LocalDateTime planExecuteTime;
    private LocalDateTime actualExecuteTime;
    private Integer status;
    private Integer isAbnormal;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
