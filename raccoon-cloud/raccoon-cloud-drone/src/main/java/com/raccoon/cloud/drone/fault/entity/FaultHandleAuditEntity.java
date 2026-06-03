package com.raccoon.cloud.drone.fault.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fault_handle_audit")
public class FaultHandleAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String faultLevel;
    private String responseAction;
    private String expandScopeJson;
    private String assignedTerminalIds;
    private String reinspectTaskId;
    private String citedRegulationIds;
    private String graphImpactSummary;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    private String handleResult;
    private String detailJson;
}
