package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 巡检工单步骤详情 inspection_work_order_detail。
 */
@Data
@TableName("inspection_work_order_detail")
public class InspectionWorkOrderDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Integer stepOrder;
    /** path / stop / collect / report */
    private String stepType;
    private String target;
    private String description;
    private Long deviceId;
    private String deviceName;
    private String checkItem;
    private BigDecimal standardMin;
    private BigDecimal standardMax;
    private String unit;
    private String actualValue;
    /** 0 正常 1 异常 */
    private Integer isException;
    private LocalDateTime collectTime;
    private String photoUrl;
    private String remark;
}
