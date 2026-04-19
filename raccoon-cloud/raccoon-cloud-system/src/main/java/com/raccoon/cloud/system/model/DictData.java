package com.raccoon.cloud.system.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_dict_data")
public class DictData implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String dictTypeCode;

    private String dictLabel;

    private String dictValue;

    private String dictExtend;

    private Integer status;

    private Integer sort;

    private String remark;

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
