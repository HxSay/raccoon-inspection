package com.raccoon.cloud.system.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_dict_type")
public class DictType implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String dictTypeCode;

    private String dictTypeName;

    private Integer status;

    private String remark;

    private Integer sort;

    private Long tenantId;

    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
