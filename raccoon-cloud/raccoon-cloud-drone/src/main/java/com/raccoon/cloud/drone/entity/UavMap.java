package com.raccoon.cloud.drone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("uav_map")
public class UavMap {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mapName;
    private String sceneType;
    private String remark;
}
