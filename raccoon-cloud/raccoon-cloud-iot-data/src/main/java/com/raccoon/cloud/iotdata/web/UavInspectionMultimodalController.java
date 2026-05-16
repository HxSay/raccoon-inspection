package com.raccoon.cloud.iotdata.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.iotdata.dto.UavInspectionSampleVO;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadRequest;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadResult;
import com.raccoon.cloud.iotdata.entity.UavInspectionSession;
import com.raccoon.cloud.iotdata.service.UavInspectionMultimodalService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** 边缘巡检会话分页（一次任务对应一条） */
    @GetMapping("/session/page")
    public HxResult<Page<UavInspectionSession>> sessionPage(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "uavId", required = false) Long uavId,
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "planId", required = false) Long planId
    ) {
        return HxResult.success(uavInspectionMultimodalService.pageSessions(current, size, uavId, taskId, planId));
    }

    /** 某次会话下的多模态采样明细 */
    @GetMapping("/session/{sessionId}/samples")
    public HxResult<List<UavInspectionSampleVO>> samples(@PathVariable Long sessionId) {
        return HxResult.success(uavInspectionMultimodalService.listSamplesBySessionId(sessionId));
    }
}
