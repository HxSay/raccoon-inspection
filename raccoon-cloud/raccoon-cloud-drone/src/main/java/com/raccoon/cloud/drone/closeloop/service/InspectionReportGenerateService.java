package com.raccoon.cloud.drone.closeloop.service;

import com.raccoon.cloud.drone.closeloop.dto.CapturedPointDTO;
import com.raccoon.cloud.drone.closeloop.dto.CloseLoopReportRequest;
import com.raccoon.cloud.drone.closeloop.dto.InspectionReportVO;
import com.raccoon.cloud.drone.closeloop.dto.IntegrityVerifyResult;
import com.raccoon.cloud.drone.closeloop.dto.TaskCollectSummary;
import com.raccoon.cloud.drone.closeloop.enums.CloseLoopStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 步骤3：巡检报告自动生成（规则模板摘要；可后续接入 LLM）。
 */
@Slf4j
@Service
public class InspectionReportGenerateService {

    public InspectionReportVO generate(CloseLoopReportRequest req,
                                       TaskCollectSummary summary,
                                       IntegrityVerifyResult verify,
                                       CloseLoopStatus status) {
        InspectionReportVO report = new InspectionReportVO();
        report.setTaskId(req.getTaskId());
        report.setTaskName(StringUtils.hasText(req.getTaskName()) ? req.getTaskName() : ("任务 " + req.getTaskId()));
        report.setTerminalCount(summary.getTerminalCount());
        report.setTotalDistanceM(summary.getTotalDistanceM());
        report.setDurationSec(summary.getDurationSec());
        report.setPlannedWaypointCount(summary.getPlannedWaypointCount());
        report.setFinishedWaypointCount(summary.getFinishedWaypointCount());
        report.setMissedDeviceCount(verify.getMissedDeviceIds().size());
        report.setDataGapCount(verify.getDataGaps().size());

        int defects = 0;
        for (CapturedPointDTO p : summary.getCapturedPoints()) {
            if (p.isHasDefect()) {
                defects++;
            }
        }
        report.setDefectCount(defects);
        report.setCompletionRate(verify.getCompletionRate());
        report.setPassed(verify.isPassed());
        report.setCloseStatus(status.getCode());
        report.setExecutiveSummary(buildSummary(report, verify, status));
        report.setGenerateTime(LocalDateTime.now());
        return report;
    }

    private String buildSummary(InspectionReportVO r, IntegrityVerifyResult verify, CloseLoopStatus status) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次巡检由 ").append(r.getTerminalCount()).append(" 台终端执行，");
        sb.append("计划航点 ").append(r.getPlannedWaypointCount())
                .append("，完成 ").append(r.getFinishedWaypointCount()).append("，");
        sb.append(String.format("完成率 %.0f%%。", r.getCompletionRate() * 100));
        if (r.getDefectCount() > 0) {
            sb.append("AI 检出异常 ").append(r.getDefectCount()).append(" 处。");
        } else {
            sb.append("AI 检测未见明显异常。");
        }
        switch (status) {
            case COMPLETED -> sb.append("点位/设备/多模态数据均已达标，任务闭环完成。");
            case PARTIAL -> sb.append("存在漏检设备 ").append(r.getMissedDeviceCount())
                    .append(" 个、数据缺口 ").append(r.getDataGapCount())
                    .append(" 项，已自动生成补检任务。");
            case MANUAL_REQUIRED -> sb.append("多轮补检仍未达标，已转人工介入。");
            case COLLECTING -> sb.append("部分终端结果未回收，待超时后强制核对。");
        }
        return sb.toString();
    }
}
