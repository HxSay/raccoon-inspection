package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电网巡检设备信息表 device（与 device_info 字段对齐，可同步数据）。
 */
@Data
@TableName("device")
public class GridDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceCode;
    private String deviceName;
    private String model;
    private String serialNumber;
    private Long categoryId;
    private Long deptId;
    private Long managerUserId;
    private Long supplierId;
    private String location;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String qrcodeUrl;
    private LocalDate purchaseDate;
    private LocalDate activateDate;
    private LocalDate warrantyEndDate;
    private Integer status;
    private Integer inspectionCycle;
    private Integer maintenanceCycle;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
