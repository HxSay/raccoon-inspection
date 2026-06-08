package com.raccoon.cloud.drone.closeloop.service;

import com.raccoon.cloud.drone.closeloop.dto.CloseLoopReportRequest;
import com.raccoon.cloud.drone.closeloop.dto.CloseLoopResult;
import com.raccoon.cloud.drone.closeloop.dto.InspectionReportVO;
import com.raccoon.cloud.drone.closeloop.dto.IntegrityVerifyResult;
import com.raccoon.cloud.drone.closeloop.dto.RescheduleResult;
import com.raccoon.cloud.drone.closeloop.dto.TaskCollectSummary;
import com.raccoon.cloud.drone.closeloop.enums.CloseLoopStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任务规划 Agent 闭环编排器：串联「结果回收 → 完整性核对 → 报告生成 → 数据归档 → 未完成项重调度 → 工单闭环 → 审计」全流程。
 * <p>对应 PDF《任务规划Agent的智能巡检任务闭环》步骤1~7。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCloseLoopOrchestrator {

    private final TaskResultCollectService collectService;
    private final TaskIntegrityVerifyService verifyService;
    private final InspectionReportGenerateService reportService;
    private final CloseLoopRescheduleService rescheduleService;
    private final WorkOrderCloseLoopService workOrderService;
    private final CloseLoopAuditService auditService;

    public CloseLoopResult close(CloseLoopReportRequest req) {
        if (req == null || req.getTaskId() == null || req.getTaskId().isBlank()) {
            return CloseLoopResult.fail(null, "taskId 不能为空");
        }
        log.info("[closeloop] ===== 任务闭环开始 taskId={} workOrderId={} =====",
                req.getTaskId(), req.getWorkOrderId());

        // 步骤1：多机结果回收
        TaskCollectSummary summary = collectService.collect(req);

        // 步骤2：完整性核对
        IntegrityVerifyResult verify = verifyService.verify(req, summary);

        // 步骤5（预判）：决定是否需要补检 + 判定闭环状态
        boolean reachedRetryLimit = (req.getRescheduleCount() != null ? req.getRescheduleCount() : 0)
                >= rescheduleService.getMaxRescheduleRetry();
        boolean skipReschedule = Boolean.TRUE.equals(req.getSkipReschedule());

        RescheduleResult reschedule = RescheduleResult.notNeeded();
        CloseLoopStatus status;
        if (verify.isPassed()) {
            status = CloseLoopStatus.COMPLETED;
        } else if (reachedRetryLimit) {
            status = CloseLoopStatus.MANUAL_REQUIRED;
        } else if (skipReschedule) {
            status = CloseLoopStatus.PARTIAL;
        } else {
            reschedule = rescheduleService.reschedule(req, verify);
            status = reschedule.isSuccess() ? CloseLoopStatus.PARTIAL : CloseLoopStatus.MANUAL_REQUIRED;
        }

        // 步骤3：报告生成
        InspectionReportVO report = reportService.generate(req, summary, verify, status);

        // 步骤4 数据分层归档 + 步骤6 工单闭环
        workOrderService.closeWorkOrder(req, report, verify);

        // 组装结果
        CloseLoopResult result = new CloseLoopResult();
        result.setSuccess(true);
        result.setTaskId(req.getTaskId());
        result.setCloseStatus(status.getCode());
        result.setCompletionRate(verify.getCompletionRate());
        result.setReport(report);
        result.setVerify(verify);
        result.setRescheduled(reschedule.isNeeded() && reschedule.isSuccess());
        result.setReinspectTaskId(reschedule.getReinspectTaskId());
        result.setReinspectRoutePayload(reschedule.getRoutePayload());
        result.setMessage(buildMessage(status, report, reschedule));

        // 步骤7：审计落库
        auditService.persist(req, result, verify, reschedule);

        log.info("[closeloop] ===== 任务闭环完成 taskId={} status={} rate={} reschedule={} =====",
                req.getTaskId(), status.getCode(), result.getCompletionRate(), result.isRescheduled());
        return result;
    }

    private String buildMessage(CloseLoopStatus status, InspectionReportVO report, RescheduleResult reschedule) {
        String head = String.format("任务闭环判定：%s，完成率 %.0f%%。",
                status.getLabel(), report.getCompletionRate() * 100);
        if (status == CloseLoopStatus.PARTIAL && reschedule.isSuccess()) {
            return head + "已生成补检任务 " + reschedule.getReinspectTaskId()
                    + "（纳入 " + reschedule.getMissedDeviceIds().size() + " 个未完成设备）。";
        }
        if (status == CloseLoopStatus.MANUAL_REQUIRED) {
            return head + (reschedule.getMessage() != null ? reschedule.getMessage() : "已转人工介入。");
        }
        return head;
    }
}
