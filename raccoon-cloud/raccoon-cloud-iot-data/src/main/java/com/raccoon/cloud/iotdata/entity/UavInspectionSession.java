package com.raccoon.cloud.iotdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("uav_inspection_session")
public class UavInspectionSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uavId;
    private Long taskId;
    private Long planId;
    private Long mapId;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private Float distanceM;
    private Integer sampleCount;

    @TableField("create_time")
    private LocalDateTime createTime;
}
