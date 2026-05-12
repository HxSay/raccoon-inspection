package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_warehouse")
public class InspectionWarehouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String warehouseName;
    private String location;
    private Long managerUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
