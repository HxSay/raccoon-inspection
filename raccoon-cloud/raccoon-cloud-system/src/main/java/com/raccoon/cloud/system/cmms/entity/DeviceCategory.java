package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_category")
public class DeviceCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String categoryName;
    private String description;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
