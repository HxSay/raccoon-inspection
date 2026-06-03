package com.raccoon.cloud.drone.fault.web;

import com.raccoon.cloud.drone.fault.dto.EnvironmentAlertReport;
import com.raccoon.cloud.drone.fault.dto.FaultAuditVO;
import com.raccoon.cloud.drone.fault.dto.FaultEventReport;
import com.raccoon.cloud.drone.fault.dto.FaultHandleResult;
import com.raccoon.cloud.drone.fault.dto.FaultResponsePlan;
import com.raccoon.cloud.drone.fault.service.FaultEventIngestService;
import com.raccoon.cloud.drone.fault.service.FaultHandleAuditService;
import com.raccoon.cloud.drone.fault.service.SpecialScenarioHandler;
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
import java.util.Optional;

/**
 * 故障分级处理 REST（网关前缀 /drone/fault）。
 */
@RestController
@RequestMapping("/fault")
@RequiredArgsConstructor
public class FaultHandleController {

    private final FaultEventIngestService ingestService;
    private final SpecialScenarioHandler specialScenarioHandler;
    private final FaultHandleAuditService auditService;

    @PostMapping("/ingest")
    public HxResult<FaultHandleResult> ingest(@Valid @RequestBody FaultEventReport report) {
        return HxResult.success(ingestService.ingest(report));
    }

    @PostMapping("/scenario/offline")
    public HxResult<FaultHandleResult> offline(
            @RequestParam Long terminalId,
            @RequestParam(defaultValue = "60") long offlineDurationSec) {
        Optional<FaultHandleResult> r = specialScenarioHandler.handleDeviceOffline(terminalId, offlineDurationSec);
        return r.map(HxResult::success).orElseGet(() -> HxResult.success(null));
    }

    @PostMapping("/scenario/disaster")
    public HxResult<FaultHandleResult> disaster(@Valid @RequestBody EnvironmentAlertReport alert) {
        return HxResult.success(specialScenarioHandler.handleEnvironmentDisaster(alert));
    }

    @GetMapping("/scenario/offline/preview/{terminalId}")
    public HxResult<FaultResponsePlan> previewOffline(@PathVariable Long terminalId) {
        return specialScenarioHandler.previewOfflinePlan(terminalId)
                .map(HxResult::success)
                .orElseGet(() -> HxResult.notFound());
    }

    @GetMapping("/audit/recent")
    public HxResult<List<FaultAuditVO>> recentAudits(@RequestParam(defaultValue = "20") int limit) {
        return HxResult.success(auditService.listRecent(limit));
    }
}
