package com.raccoon.common.dto.planning;

import lombok.Data;

@Data
public class SimulationPhotoDTO {

    private String id;
    private Integer waypointIndex;
    private Double latitude;
    private Double longitude;
    private Double altitudeM;
    /** 可见光缩略图 data URL（可选，长度受限） */
    private String previewDataUrl;
}
