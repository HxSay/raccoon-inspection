package com.raccoon.cloud.drone.closeloop.service;

import com.raccoon.cloud.drone.closeloop.dto.CloseLoopReportRequest;
import com.raccoon.cloud.drone.closeloop.dto.InspectionReportVO;
import com.raccoon.cloud.drone.closeloop.dto.IntegrityVerifyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 步骤4 数据分层归档 + 步骤6 工单闭环与状态同步。
 * <p>说明：CMMS 工单步骤/巡检记录的回填由 system 侧 {@code complete-simulation} 完成，
 * 本服务负责闭环侧的归档登记与工单闭环状态判定，避免重复回写远端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderCloseLoopService {

    public void closeWorkOrder(CloseLoopReportRequest req, InspectionReportVO report,
                               IntegrityVerifyResult verify) {
        // 步骤4：分层归档（报告元数据 + 设备最近巡检状态）。
        log.info("[closeloop-archive] 归档报告 taskId={} 设备覆盖={}/{} 数据缺口={} 完成率={}",
                req.getTaskId(),
                (req.getPlannedDeviceIds() != null ? req.getPlannedDeviceIds().size() : 0) - verify.getMissedDeviceIds().size(),
                req.getPlannedDeviceIds() != null ? req.getPlannedDeviceIds().size() : 0,
                verify.getDataGaps().size(),
                String.format("%.2f", verify.getCompletionRate()));

        // 步骤6：工单闭环状态同步。
        String orderStatus = report.isPassed() ? "COMPLETED" : "PARTIAL_COMPLETED";
        if (req.getWorkOrderId() != null) {
            log.info("[closeloop-workorder] 工单闭环 workOrderId={} status={} reportTask={}",
                    req.getWorkOrderId(), orderStatus, req.getTaskId());
        }
        // 推送验收通知（占位：复用日志告警，后续可接入站内信/移动APP）。
        log.info("[closeloop-notify] 推送巡检验收通知 taskId={} 结论={}",
                req.getTaskId(), report.getCloseStatus());
    }
}
