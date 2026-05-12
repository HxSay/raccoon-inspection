package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String supplierName;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
