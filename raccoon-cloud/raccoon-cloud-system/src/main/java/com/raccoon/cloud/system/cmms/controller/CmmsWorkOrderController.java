package com.raccoon.cloud.system.cmms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.cmms.dto.WorkOrderPageRequest;
import com.raccoon.cloud.system.cmms.entity.WorkOrder;
import com.raccoon.cloud.system.cmms.service.WorkOrderService;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 设备维修/保养工单（work_order）。
 */
@RestController
@RequestMapping("/cmms/workOrder")
@Tag(name = "CMMS-维修工单", description = "工单维护")
public class CmmsWorkOrderController {

    private final WorkOrderService workOrderService;

    public CmmsWorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping("/page")
    public HxResult<IPage<WorkOrder>> page(@RequestBody WorkOrderPageRequest req) {
        return HxResult.success(workOrderService.page(req));
    }

    @GetMapping("/get")
    public HxResult<WorkOrder> get(@RequestParam Long id) {
        return HxResult.success(workOrderService.get(id));
    }

    @PostMapping("/save")
    public HxResult<?> save(@RequestBody WorkOrder body) {
        try {
            workOrderService.save(body);
            return HxResult.success("保存成功");
        } catch (Exception e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/delete")
    public HxResult<?> delete(@RequestParam Long id) {
        workOrderService.delete(id);
        return HxResult.success("删除成功");
    }

    @PostMapping("/status")
    public HxResult<?> status(@RequestParam Long id, @RequestParam Integer status) {
        try {
            workOrderService.updateStatus(id, status);
            return HxResult.success("状态已更新");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }
}
