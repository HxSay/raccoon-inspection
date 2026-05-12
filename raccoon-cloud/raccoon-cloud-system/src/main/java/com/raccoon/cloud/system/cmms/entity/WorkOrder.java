package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderCode;
    private Long deviceId;
    private Integer source;
    private String faultDescription;
    private String faultImageUrls;
    private Integer orderType;
    private Integer level;
    private Long assignUserId;
    private Long reportUserId;
    private LocalDateTime reportTime;
    private LocalDateTime planFinishTime;
    private LocalDateTime actualFinishTime;
    private Integer status;
    private String repairContent;
    private BigDecimal repairCost;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
