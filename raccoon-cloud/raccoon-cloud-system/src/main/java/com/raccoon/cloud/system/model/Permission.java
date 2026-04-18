package com.raccoon.cloud.system.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("um_permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String permissionName;
    private String permissionCode;
    private Integer permissionType;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}