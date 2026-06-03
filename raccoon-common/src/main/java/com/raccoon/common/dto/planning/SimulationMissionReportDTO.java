package com.raccoon.common.dto.planning;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 仿真任务完成上报（边缘 → CMMS），与 raccoon-drone-sim MissionReport 对齐。
 */
@Data
public class SimulationMissionReportDTO {

    private Double durationSec;
    private Double distanceM;
    private Integer photoCount;
    private Integer telemetrySent;
    private Boolean multimodalUploaded;
    private String multimodalError;

    private List<SimulationPhotoDTO> photos = new ArrayList<>();
    private List<SimulationAiResultDTO> aiResults = new ArrayList<>();
    private List<SimulationMultimodalDTO> multimodalSamples = new ArrayList<>();
}
