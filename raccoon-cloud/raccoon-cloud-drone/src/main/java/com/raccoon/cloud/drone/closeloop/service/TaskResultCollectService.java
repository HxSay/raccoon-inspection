package com.raccoon.cloud.drone.closeloop.service;

import com.raccoon.cloud.drone.closeloop.dto.CapturedPointDTO;
import com.raccoon.cloud.drone.closeloop.dto.CloseLoopReportRequest;
import com.raccoon.cloud.drone.closeloop.dto.TaskCollectSummary;
import com.raccoon.cloud.drone.closeloop.dto.TerminalTaskResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 步骤1：多机巡检结果回收。
 * <p>聚合各终端执行摘要 + 边缘上报的点位采集明细，形成统一回收快照。
 */
@Slf4j
@Service
public class TaskResultCollectService {

    public TaskCollectSummary collect(CloseLoopReportRequest req) {
        TaskCollectSummary summary = new TaskCollectSummary();
        summary.setTaskId(req.getTaskId());

        List<CapturedPointDTO> captured = req.getCapturedPoints() != null
                ? req.getCapturedPoints() : List.of();
        summary.setCapturedPoints(captured);

        for (CapturedPointDTO p : captured) {
            if (p.getDeviceId() != null) {
                summary.getAllFinishedDeviceIds().add(p.getDeviceId());
            }
        }

        int finishedWp = req.getFinishedWaypointCount() != null
                ? req.getFinishedWaypointCount()
                : (int) captured.stream().map(CapturedPointDTO::getWaypointIndex)
                        .filter(java.util.Objects::nonNull).distinct().count();
        summary.setFinishedWaypointCount(finishedWp);
        summary.setPlannedWaypointCount(req.getPlannedWaypointCount() != null
                ? req.getPlannedWaypointCount() : finishedWp);

        List<TerminalTaskResultDTO> terminals = req.getTerminals() != null
                ? req.getTerminals() : List.of();
        summary.setTerminalCount(Math.max(terminals.size(), 1));
        summary.setTotalDistanceM(req.getFlightDistanceM() != null ? req.getFlightDistanceM()
                : terminals.stream().mapToDouble(t -> t.getFlightDistanceM() != null ? t.getFlightDistanceM() : 0).sum());
        summary.setDurationSec(req.getDurationSec());

        for (TerminalTaskResultDTO t : terminals) {
            if (t.getFinishedDeviceIds() != null) {
                summary.getAllFinishedDeviceIds().addAll(t.getFinishedDeviceIds());
            }
        }

        // 终端未全部上报：超时强制核对（多机协同场景）
        summary.setAllTerminalReported(terminals.isEmpty()
                || terminals.stream().allMatch(t -> t.getStatus() != null
                && t.getStatus() != com.raccoon.cloud.drone.closeloop.enums.TerminalExecStatus.TIMEOUT));

        log.info("[closeloop-collect] taskId={} terminals={} finishedWp={}/{} devices={}",
                summary.getTaskId(), summary.getTerminalCount(),
                summary.getFinishedWaypointCount(), summary.getPlannedWaypointCount(),
                summary.getAllFinishedDeviceIds().size());
        return summary;
    }
}
