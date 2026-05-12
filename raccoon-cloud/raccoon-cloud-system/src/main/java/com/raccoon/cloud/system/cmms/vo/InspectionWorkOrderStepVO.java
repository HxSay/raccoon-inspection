package com.raccoon.cloud.system.cmms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单步骤出参（与前端 JSON 字段对齐：type、standardMin/Max、deviceCode 等）。
 */
@Data
public class InspectionWorkOrderStepVO {
    private Long id;
    private Long orderId;
    private Integer stepOrder;
    /** 同 step_type，对外统一为 type */
    private String type;
    private String target;
    private String description;
    private Long deviceId;
    private String deviceName;
    /** 来自 device 表关联，不入详情库 */
    private String deviceCode;
    /** 预留展示字段，无库字段时为空 */
    private String voltageLevel;
    private String checkItem;
    private BigDecimal standardMin;
    private BigDecimal standardMax;
    private String unit;
    /** 创建时前端传入，不落库，仅缓存于 description 前缀不优雅；此处执行/详情接口由前端再次带或为空 */
    private String detectionMethod;
    private String actualValue;
    private Integer isException;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime collectTime;
    private String photoUrl;
    private String remark;
}
