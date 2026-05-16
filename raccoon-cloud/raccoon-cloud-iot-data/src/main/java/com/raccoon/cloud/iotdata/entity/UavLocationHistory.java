package com.raccoon.cloud.iotdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("uav_location_history")
public class UavLocationHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uavId;
    private Long taskId;
    private Long mapId;

    private BigDecimal longitude;
    private BigDecimal latitude;

    private Float height;
    private Float speed;
    private Float battery;

    private Integer locationMode;

    /** 飞行状态：mission / rth / idle 等 */
    private String flightStatus;

    @TableField("create_time")
    private LocalDateTime createTime;
}
