package com.raccoon.cloud.drone.fault.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.fault.entity.FaultEventEntity;
import com.raccoon.cloud.drone.fault.mapper.FaultEventMapper;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FaultEventPersistenceService {

    private final FaultEventMapper faultEventMapper;
    private final ObjectMapper objectMapper;

    public void persist(FaultEvent event) {
        FaultEventEntity row = new FaultEventEntity();
        row.setEventId(event.getEventId());
        row.setDeviceId(event.getDeviceId());
        row.setSourceTerminalId(event.getSourceTerminalId());
        row.setMapId(event.getMapId());
        row.setFaultType(event.getFaultType());
        row.setFaultLevel(event.getLevel() != null ? event.getLevel().getCode() : null);
        row.setConfidence(event.getConfidence());
        row.setSeverityScore(event.getSeverityScore());
        row.setDescription(event.getDescription());
        row.setEvidenceUrl(event.getEvidenceUrl());
        row.setDetectTime(event.getDetectTime() != null ? event.getDetectTime() : LocalDateTime.now());
        row.setMerged(false);
        try {
            if (event.getExtData() != null && !event.getExtData().isEmpty()) {
                row.setExtDataJson(objectMapper.writeValueAsString(event.getExtData()));
            }
        } catch (Exception ignored) {
        }
        faultEventMapper.insert(row);
    }

    public void updateLevel(FaultEvent event) {
        faultEventMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FaultEventEntity>()
                .eq(FaultEventEntity::getEventId, event.getEventId())
                .set(FaultEventEntity::getFaultLevel, event.getLevel() != null ? event.getLevel().getCode() : null)
                .set(FaultEventEntity::getSeverityScore, event.getSeverityScore()));
    }

    public boolean isDuplicate(FaultEvent event) {
        if (event.getDeviceId() == null || event.getFaultType() == null) {
            return false;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(5);
        Long count = faultEventMapper.selectCount(new LambdaQueryWrapper<FaultEventEntity>()
                .eq(FaultEventEntity::getDeviceId, event.getDeviceId())
                .eq(FaultEventEntity::getFaultType, event.getFaultType())
                .ge(FaultEventEntity::getDetectTime, since)
                .eq(FaultEventEntity::getMerged, false));
        return count != null && count > 0;
    }
}
