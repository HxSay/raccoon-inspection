package com.raccoon.common.dto.planning;

import lombok.Data;

import java.util.Map;

@Data
public class SimulationMultimodalDTO {

    private Integer waypointIndex;
    private String modalityType;
    private Map<String, Object> payload;
    /** 可见光/热成像缩略图（可选） */
    private String previewDataUrl;
}
