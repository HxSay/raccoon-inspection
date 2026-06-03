package com.raccoon.cloud.drone.fault.model;

import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 内存态故障事件（定级流水线上下文，与 PDF FaultEvent 对齐）。
 */
@Data
public class FaultEvent {

    private String eventId;
    private Long deviceId;
    private Long sourceTerminalId;
    private Long mapId;
    private String faultType;
    private FaultLevel level;
    private Double confidence;
    private Double severityScore;
    private String description;
    private String evidenceUrl;
    private LocalDateTime detectTime;
    private Map<String, Object> extData = new HashMap<>();
}
