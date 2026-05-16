package com.raccoon.cloud.iotdata.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.iotdata.dto.UavInspectionSampleVO;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadRequest;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadResult;
import com.raccoon.cloud.iotdata.entity.UavInspectionSession;

import java.util.List;

public interface UavInspectionMultimodalService {

    UavInspectionUploadResult uploadMissionResult(UavInspectionUploadRequest request);

    Page<UavInspectionSession> pageSessions(long current, long size, Long uavId, Long taskId, Long planId);

    List<UavInspectionSampleVO> listSamplesBySessionId(Long sessionId);
}
