package com.raccoon.cloud.system.cmms.controller;

import com.raccoon.cloud.system.cmms.dto.AgentSimulationCompleteRequest;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrder;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderMapper;
import com.raccoon.cloud.system.cmms.service.InspectionWorkOrderService;
import com.raccoon.cloud.system.cmms.service.MobileAuditService;
import com.raccoon.cloud.system.cmms.service.WorkOrderGenerateService;
import com.raccoon.common.dto.planning.PlanningWorkOrderSubmitRequest;
import com.raccoon.common.dto.planning.PlanningWorkOrderSubmitResponse;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务规划 Agent 内部接口：提交待审核工单。
 */
@RestController
@RequestMapping("/cmms/agent/planning")
@Tag(name = "CMMS-Agent规划", description = "Agent 工单生成与重提交")
@RequiredArgsConstructor
public class CmmsAgentPlanningController {

    private final WorkOrderGenerateService workOrderGenerateService;
    private final MobileAuditService mobileAuditService;
    private final InspectionWorkOrderService inspectionWorkOrderService;
    private final InspectionWorkOrderMapper orderMapper;

    @PostMapping("/submit")
    public HxResult<PlanningWorkOrderSubmitResponse> submit(@RequestBody PlanningWorkOrderSubmitRequest req) {
        try {
            PlanningWorkOrderSubmitResponse resp = workOrderGenerateService.submitFromPlanning(req);
            InspectionWorkOrder order = orderMapper.selectById(resp.getWorkOrderId());
            if (order != null) {
                mobileAuditService.notifyOnSubmit(order);
            }
            return HxResult.success(resp);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/resubmit")
    public HxResult<PlanningWorkOrderSubmitResponse> resubmit(
            @RequestParam Long workOrderId,
            @RequestBody PlanningWorkOrderSubmitRequest req) {
        try {
            PlanningWorkOrderSubmitResponse resp = workOrderGenerateService.resubmitAfterReplan(workOrderId, req);
            InspectionWorkOrder order = orderMapper.selectById(resp.getWorkOrderId());
            if (order != null) {
                mobileAuditService.notifyOnSubmit(order);
            }
            return HxResult.success(resp);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/store-payload")
    public HxResult<Void> storePayload(@RequestParam Long workOrderId, @RequestBody String payloadJson) {
        workOrderGenerateService.storePlanningPayload(workOrderId, payloadJson);
        return HxResult.success();
    }

    @PostMapping("/complete-simulation")
    public HxResult<?> completeSimulation(@RequestBody AgentSimulationCompleteRequest req) {
        try {
            inspectionWorkOrderService.completeAfterSimulation(req);
            return HxResult.success("工单已标记为已完成");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }
}
