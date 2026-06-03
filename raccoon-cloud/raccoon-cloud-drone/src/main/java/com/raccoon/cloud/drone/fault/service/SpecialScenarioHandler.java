package com.raccoon.cloud.drone.fault.service;

import com.raccoon.cloud.drone.fault.dto.EnvironmentAlertReport;
import com.raccoon.cloud.drone.fault.dto.FaultEventReport;
import com.raccoon.cloud.drone.fault.dto.FaultHandleResult;
import com.raccoon.cloud.drone.fault.dto.FaultResponsePlan;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备离线、环境灾害等特殊场景。
 */
@Service
@RequiredArgsConstructor
public class SpecialScenarioHandler {

    private final FaultResponsePlanService planService;
    private final FaultEventIngestService ingestService;

    private final Map<Long, LocalDateTime> terminalOfflineSince = new ConcurrentHashMap<>();

    public void markTerminalOffline(Long terminalId) {
        terminalOfflineSince.putIfAbsent(terminalId, LocalDateTime.now());
    }

    public void markTerminalOnline(Long terminalId) {
        terminalOfflineSince.remove(terminalId);
    }

    public Optional<FaultHandleResult> handleDeviceOffline(Long terminalId, long offlineDurationSec) {
        if (offlineDurationSec <= 30) {
            return Optional.empty();
        }
        FaultEventReport report = new FaultEventReport();
        report.setTerminalId(terminalId);
        report.setFaultType("TERMINAL_OFFLINE");
        report.setConfidence(1.0);
        report.setDescription("巡检终端离线超过 " + offlineDurationSec + " 秒");
        return Optional.of(ingestService.ingest(report));
    }

    public FaultHandleResult handleEnvironmentDisaster(EnvironmentAlertReport alert) {
        FaultEventReport report = new FaultEventReport();
        report.setMapId(alert.getMapId());
        report.setFaultType("DISASTER");
        report.setConfidence(alert.getSeverity() != null ? alert.getSeverity() : 0.95);
        report.setDescription(alert.getDescription() != null ? alert.getDescription()
                : "环境灾害告警:" + alert.getAlertType());
        return ingestService.ingest(report);
    }

    public Optional<FaultResponsePlan> previewOfflinePlan(Long terminalId) {
        FaultEvent event = new FaultEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setSourceTerminalId(terminalId);
        event.setFaultType("TERMINAL_OFFLINE");
        event.setDetectTime(LocalDateTime.now());
        return Optional.of(planService.buildPlan(event, FaultLevel.SERIOUS));
    }
}
