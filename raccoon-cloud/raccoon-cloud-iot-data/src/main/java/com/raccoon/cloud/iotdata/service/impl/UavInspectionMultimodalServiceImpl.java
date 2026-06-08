package com.raccoon.cloud.iotdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.iotdata.dto.UavInspectionSampleVO;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadRequest;
import com.raccoon.cloud.iotdata.dto.UavInspectionUploadResult;
import com.raccoon.cloud.iotdata.entity.UavInspectionSample;
import com.raccoon.cloud.iotdata.entity.UavInspectionSession;
import com.raccoon.cloud.iotdata.integration.UavInspectionRagArchiveClient;
import com.raccoon.cloud.iotdata.mapper.UavInspectionSampleMapper;
import com.raccoon.cloud.iotdata.mapper.UavInspectionSessionMapper;
import com.raccoon.cloud.iotdata.service.UavInspectionGraphArchiveService;
import com.raccoon.cloud.iotdata.service.UavInspectionMultimodalService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UavInspectionMultimodalServiceImpl extends ServiceImpl<UavInspectionSessionMapper, UavInspectionSession>
        implements UavInspectionMultimodalService {

    private final UavInspectionSampleMapper sampleMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<UavInspectionGraphArchiveService> graphArchiveServiceProvider;
    private final UavInspectionRagArchiveClient ragArchiveClient;

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

        UavInspectionGraphArchiveService graphArchiveService = graphArchiveServiceProvider.getIfAvailable();
        UavInspectionGraphArchiveService.GraphArchiveResult graphResult = graphArchiveService == null
                ? new UavInspectionGraphArchiveService.GraphArchiveResult(false, "Neo4j 归档未启用")
                : graphArchiveService.archive(session, rows);
        UavInspectionRagArchiveClient.ArchiveResult ragResult = ragArchiveClient.archive(session, rows);
        return new UavInspectionUploadResult(
                session.getId(),
                rows.size(),
                graphResult.success(),
                graphResult.message(),
                ragResult.success(),
                ragResult.message(),
                ragResult.docId(),
                ragResult.chunkCount()
        );
    }

    @Override
    public Page<UavInspectionSession> pageSessions(long current, long size, Long uavId, Long taskId, Long planId) {
        LambdaQueryWrapper<UavInspectionSession> w = new LambdaQueryWrapper<>();
        w.eq(uavId != null, UavInspectionSession::getUavId, uavId);
        w.eq(taskId != null, UavInspectionSession::getTaskId, taskId);
        w.eq(planId != null, UavInspectionSession::getPlanId, planId);
        w.orderByDesc(UavInspectionSession::getFinishedAt);
        return page(new Page<>(current, size), w);
    }

    @Override
    public List<UavInspectionSampleVO> listSamplesBySessionId(Long sessionId) {
        LambdaQueryWrapper<UavInspectionSample> w = new LambdaQueryWrapper<>();
        w.eq(UavInspectionSample::getSessionId, sessionId);
        w.orderByAsc(UavInspectionSample::getWaypointIndex);
        w.orderByAsc(UavInspectionSample::getModalityType);
        List<UavInspectionSample> rows = sampleMapper.selectList(w);
        List<UavInspectionSampleVO> result = new ArrayList<>();
        for (UavInspectionSample row : rows) {
            UavInspectionSampleVO vo = new UavInspectionSampleVO();
            vo.setId(row.getId());
            vo.setSessionId(row.getSessionId());
            vo.setWaypointIndex(row.getWaypointIndex());
            vo.setModalityType(row.getModalityType());
            vo.setCapturedAt(row.getCapturedAt());
            vo.setLongitude(row.getLongitude());
            vo.setLatitude(row.getLatitude());
            vo.setHeight(row.getHeight());
            vo.setPayload(parsePayload(row.getPayloadJson()));
            result.add(vo);
        }
        return result;
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of("raw", json);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload 序列化失败: " + e.getMessage());
        }
    }
}
