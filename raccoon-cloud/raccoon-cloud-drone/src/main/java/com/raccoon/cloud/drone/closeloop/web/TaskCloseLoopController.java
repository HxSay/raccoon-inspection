package com.raccoon.cloud.drone.closeloop.web;

import com.raccoon.cloud.drone.closeloop.dto.CloseLoopReportRequest;
import com.raccoon.cloud.drone.closeloop.dto.CloseLoopResult;
import com.raccoon.cloud.drone.closeloop.entity.TaskCloseLoopAuditEntity;
import com.raccoon.cloud.drone.closeloop.service.CloseLoopAuditPdfExportService;
import com.raccoon.cloud.drone.closeloop.service.CloseLoopAuditService;
import com.raccoon.cloud.drone.closeloop.service.TaskCloseLoopOrchestrator;
import com.raccoon.common.result.HxResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 任务规划 Agent 巡检任务闭环 REST（网关前缀 /drone/closeloop）。
 */
@RestController
@RequestMapping("/closeloop")
@RequiredArgsConstructor
public class TaskCloseLoopController {

    private final TaskCloseLoopOrchestrator orchestrator;
    private final CloseLoopAuditService auditService;
    private final CloseLoopAuditPdfExportService pdfExportService;

    /**
     * 任务完成上报，触发完整性核对、报告生成、未完成项补检、闭环审计。
     */
    @PostMapping("/ingest")
    public HxResult<CloseLoopResult> ingest(@Valid @RequestBody CloseLoopReportRequest req) {
        return HxResult.success(orchestrator.close(req));
    }

    @GetMapping("/audit/recent")
    public HxResult<List<TaskCloseLoopAuditEntity>> recent(@RequestParam(defaultValue = "30") int limit) {
        return HxResult.success(auditService.recent(limit));
    }

    @GetMapping("/audit/{taskId}")
    public HxResult<TaskCloseLoopAuditEntity> latest(@PathVariable String taskId) {
        return HxResult.success(auditService.latestByTask(taskId));
    }

    @GetMapping("/audit/export")
    public void exportAudit(@RequestParam(defaultValue = "200") int limit,
                            HttpServletResponse response) throws IOException {
        List<TaskCloseLoopAuditEntity> rows = auditService.listForExport(limit);
        String fileName = "close-loop-audit-" + System.currentTimeMillis() + ".pdf";
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\""
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20") + "\"");
        pdfExportService.export(rows, response.getOutputStream());
    }
}
