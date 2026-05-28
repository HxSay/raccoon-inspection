package com.raccoon.cloud.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("uav_inspection_device")
public class UavInspectionDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mapId;
    private String deviceName;
    private String deviceType;
    private Integer status;
}
