package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_plan")
public class InspectionPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String planName;
    /** JSON 数组字符串，如 [1,2,3] */
    private String deviceIds;
    private Integer cycleType;
    private Integer cycleValue;
    private Long execUserId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
