package com.raccoon.cloud.iotdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("uav_inspection_sample")
public class UavInspectionSample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Integer waypointIndex;
    private String modalityType;
    private LocalDateTime capturedAt;

    private BigDecimal longitude;
    private BigDecimal latitude;
    private Float height;

    private String payloadJson;

    @TableField("create_time")
    private LocalDateTime createTime;
}
