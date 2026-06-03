package com.raccoon.cloud.drone.fault.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FaultAuditVO {

    private Long id;
    private String eventId;
    private String faultLevel;
    private String responseAction;
    private String reinspectTaskId;
    private String handleResult;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;
}
