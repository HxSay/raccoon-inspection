package com.raccoon.cloud.iotdata.service;

import com.raccoon.cloud.iotdata.dto.UavInspectionUploadRequest;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadResult;

public interface UavInspectionMultimodalService {

    UavInspectionUploadResult uploadMissionResult(UavInspectionUploadRequest request);
}
