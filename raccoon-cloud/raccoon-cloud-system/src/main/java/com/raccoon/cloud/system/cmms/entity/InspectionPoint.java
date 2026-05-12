package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inspection_point")
public class InspectionPoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String pointName;
    private Integer pointType;
    private String unit;
    private BigDecimal minThreshold;
    private BigDecimal maxThreshold;
    private BigDecimal standardValue;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
