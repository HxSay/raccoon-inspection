package com.raccoon.cloud.system.cmms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inspection_record")
public class InspectionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long deviceId;
    private Long pointId;
    private String checkValue;
    private Integer isNormal;
    /** JSON 数组 */
    private String imageUrls;
    private LocalDateTime createTime;
}
