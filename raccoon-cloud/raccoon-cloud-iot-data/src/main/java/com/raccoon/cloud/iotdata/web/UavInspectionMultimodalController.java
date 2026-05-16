package com.raccoon.cloud.iotdata.web;

import com.raccoon.cloud.iotdata.dto.UavInspectionUploadRequest;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadResult;
import com.raccoon.cloud.iotdata.service.UavInspectionMultimodalService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 边缘巡检多模态结果上报（任务结束后批量入库）。
 */
@RestController
@RequestMapping("/uav-inspection")
@RequiredArgsConstructor
public class UavInspectionMultimodalController {

    private final UavInspectionMultimodalService uavInspectionMultimodalService;

    @PostMapping("/multimodal/upload")
    public HxResult<UavInspectionUploadResult> upload(@Valid @RequestBody UavInspectionUploadRequest request) {
        return HxResult.success(uavInspectionMultimodalService.uploadMissionResult(request));
    }
}
