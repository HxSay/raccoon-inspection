package com.raccoon.cloud.drone.fault.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fault_event")
public class FaultEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private Long deviceId;
    private Long sourceTerminalId;
    private Long mapId;
    private String faultType;
    private String faultLevel;
    private Double confidence;
    private Double severityScore;
    private String description;
    private String evidenceUrl;
    private String extDataJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime detectTime;

    private Boolean merged;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
