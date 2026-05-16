package com.raccoon.cloud.iotdata.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadRequest;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadResult;
import com.raccoon.cloud.iotdata.entity.UavInspectionSample;
import com.raccoon.cloud.iotdata.entity.UavInspectionSession;
import com.raccoon.cloud.iotdata.mapper.UavInspectionSampleMapper;
import com.raccoon.cloud.iotdata.mapper.UavInspectionSessionMapper;
import com.raccoon.cloud.iotdata.service.UavInspectionMultimodalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UavInspectionMultimodalServiceImpl extends ServiceImpl<UavInspectionSessionMapper, UavInspectionSession>
        implements UavInspectionMultimodalService {

    private final UavInspectionSampleMapper sampleMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UavInspectionUploadResult uploadMissionResult(UavInspectionUploadRequest request) {
        UavInspectionUploadRequest.Session s = request.getSession();
        UavInspectionSession session = new UavInspectionSession();
        session.setUavId(s.getUavId());
        session.setTaskId(s.getTaskId());
        session.setPlanId(s.getPlanId());
        session.setMapId(s.getMapId());
        session.setStartedAt(s.getStartedAt());
        session.setFinishedAt(s.getFinishedAt());
        session.setDistanceM(s.getDistanceM());
        session.setSampleCount(request.getSamples().size());
        session.setCreateTime(LocalDateTime.now());
        save(session);

        List<UavInspectionSample> rows = new ArrayList<>();
        for (UavInspectionUploadRequest.Sample item : request.getSamples()) {
            UavInspectionSample row = new UavInspectionSample();
            row.setSessionId(session.getId());
            row.setWaypointIndex(item.getWaypointIndex());
            row.setModalityType(item.getModalityType());
            row.setCapturedAt(item.getCapturedAt());
            row.setLongitude(item.getLongitude());
            row.setLatitude(item.getLatitude());
            row.setHeight(item.getHeight());
            row.setPayloadJson(toJson(item.getPayload()));
            row.setCreateTime(LocalDateTime.now());
            rows.add(row);
        }
        for (UavInspectionSample row : rows) {
            sampleMapper.insert(row);
        }
        return new UavInspectionUploadResult(session.getId(), rows.size());
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload 序列化失败: " + e.getMessage());
        }
    }
}
