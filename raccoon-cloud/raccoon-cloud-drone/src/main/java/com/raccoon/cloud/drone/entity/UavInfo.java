package com.raccoon.cloud.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("uav_info")
public class UavInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uavName;
    private String uavCode;
    private Long mapId;
    private Integer status;
}
