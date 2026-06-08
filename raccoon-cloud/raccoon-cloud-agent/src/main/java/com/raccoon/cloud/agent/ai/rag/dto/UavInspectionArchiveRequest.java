package com.raccoon.cloud.agent.ai.rag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class UavInspectionArchiveRequest {

    @NotNull
    @Valid
    private Session session;

    @NotEmpty
    @Valid
    private List<Sample> samples;

    @Data
    public static class Session {
        @NotNull
        private Long sessionId;
        private Long uavId;
        private Long taskId;
        private Long planId;
        private Long mapId;
        private String startedAt;
        private String finishedAt;
        private Float distanceM;
        private Integer sampleCount;
    }

    @Data
    public static class Sample {
        @NotNull
        private Long sampleId;
        private Integer waypointIndex;
        private String modalityType;
        private String capturedAt;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Float height;
        private Map<String, Object> payload;
    }
}
