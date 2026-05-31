package com.raccoon.cloud.system.cmms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrder;
import com.raccoon.cloud.system.cmms.service.MobileAuditService;
import com.raccoon.common.dto.planning.WorkOrderAuditActionRequest;
import com.raccoon.common.dto.planning.WorkOrderAuditDetailDTO;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端巡检工单审核 API。
 */
@RestController
@RequestMapping("/cmms/workOrderAudit")
@Tag(name = "CMMS-工单审核", description = "移动端审核待办")
@RequiredArgsConstructor
public class WorkOrderAuditController {

    private final MobileAuditService mobileAuditService;

    @GetMapping("/pending")
    public HxResult<IPage<InspectionWorkOrder>> pending(
            @RequestParam(required = false) Long inspectorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return HxResult.success(mobileAuditService.listPending(inspectorId, page, size));
    }

    @GetMapping("/detail")
    public HxResult<WorkOrderAuditDetailDTO> detail(@RequestParam Long workOrderId) {
        try {
            return HxResult.success(mobileAuditService.detail(workOrderId));
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/approve")
    public HxResult<?> approve(@RequestBody WorkOrderAuditActionRequest req) {
        try {
            mobileAuditService.approve(req);
            return HxResult.success("审核通过，任务已下发");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/reject")
    public HxResult<?> reject(@RequestBody WorkOrderAuditActionRequest req) {
        try {
            mobileAuditService.reject(req);
            return HxResult.success("已驳回并触发重新规划");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }
}
