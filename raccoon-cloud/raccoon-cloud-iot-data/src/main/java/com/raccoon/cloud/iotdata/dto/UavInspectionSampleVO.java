package com.raccoon.cloud.iotdata.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UavInspectionSampleVO {

    private Long id;
    private Long sessionId;
    private Integer waypointIndex;
    private String modalityType;
    private LocalDateTime capturedAt;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Float height;
    private Map<String, Object> payload;
}
