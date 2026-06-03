package com.raccoon.common.dto.planning;

import lombok.Data;

@Data
public class SimulationAiResultDTO {

    private String photoId;
    private Boolean hasDefect;
    private String label;
    private Double confidence;
}
