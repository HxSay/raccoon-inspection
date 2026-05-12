package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_dept")
public class InspectionDept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String deptName;
    private Long leaderUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
