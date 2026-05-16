package com.raccoon.cloud.iotdata.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class UavInspectionUploadRequest {

    @NotNull
    @Valid
    private Session session;

    @NotEmpty
    @Valid
    private List<Sample> samples;

    @Data
    public static class Session {
        @NotNull
        private Long uavId;
        private Long taskId;
        private Long planId;
        private Long mapId;
        @NotNull
        private LocalDateTime startedAt;
        @NotNull
        private LocalDateTime finishedAt;
        private Float distanceM;
    }

    @Data
    public static class Sample {
        @NotNull
        private Integer waypointIndex;
        @NotNull
        private String modalityType;
        @NotNull
        private LocalDateTime capturedAt;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Float height;
        @NotNull
        private Map<String, Object> payload;
    }
}
