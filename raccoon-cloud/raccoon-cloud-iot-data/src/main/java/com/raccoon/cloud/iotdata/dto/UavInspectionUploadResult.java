package com.raccoon.cloud.iotdata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UavInspectionUploadResult {
    private Long sessionId;
    private int sampleCount;
}
