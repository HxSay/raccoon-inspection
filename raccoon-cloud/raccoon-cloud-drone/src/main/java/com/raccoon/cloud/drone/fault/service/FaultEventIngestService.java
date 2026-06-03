package com.raccoon.cloud.drone.fault.service;

import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.fault.dto.FaultEventReport;
import com.raccoon.cloud.drone.fault.dto.FaultHandleResult;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import com.raccoon.cloud.drone.mapper.UavInspectionDeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FaultEventIngestService {

    private final FaultClassificationOrchestrator orchestrator;
    private final FaultEventPersistenceService persistenceService;
    private final UavInspectionDeviceMapper deviceMapper;

    @Transactional
    public FaultHandleResult ingest(FaultEventReport report) {
        FaultEvent event = normalize(report);
        if (!shouldSkipDedupe(report) && persistenceService.isDuplicate(event)) {
            return FaultHandleResult.merged(event.getEventId());
        }
        persistenceService.persist(event);
        return orchestrator.handle(event);
    }

    private FaultEvent normalize(FaultEventReport report) {
        FaultEvent event = new FaultEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setDeviceId(report.getDeviceId());
        event.setSourceTerminalId(report.getTerminalId());
        event.setMapId(report.getMapId());
        event.setFaultType(report.getFaultType() != null ? report.getFaultType().trim().toUpperCase() : "UNKNOWN");
        event.setConfidence(report.getConfidence());
        event.setDescription(report.getDescription());
        event.setEvidenceUrl(report.getDataUrl());
        event.setDetectTime(LocalDateTime.now());
        if (report.getExtData() != null) {
            event.getExtData().putAll(report.getExtData());
        }
        if (event.getMapId() == null && event.getDeviceId() != null) {
            UavInspectionDevice dev = deviceMapper.selectById(event.getDeviceId());
            if (dev != null) {
                event.setMapId(dev.getMapId());
            }
        }
        return event;
    }

    private boolean shouldSkipDedupe(FaultEventReport report) {
        if (Boolean.TRUE.equals(report.getSkipDedupe())) {
            return true;
        }
        Object sim = report.getExtData() != null ? report.getExtData().get("simulation") : null;
        return Boolean.TRUE.equals(sim) || "true".equalsIgnoreCase(String.valueOf(sim));
    }
}
