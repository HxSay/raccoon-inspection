package com.raccoon.cloud.system.cmms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.cmms.dto.*;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrder;
import com.raccoon.cloud.system.cmms.service.InspectionWorkOrderService;
import com.raccoon.cloud.system.cmms.vo.InspectionWorkOrderFullVO;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 巡检工单（inspection_work_order / detail / device）。
 */
@RestController
@RequestMapping("/cmms/inspectionWorkOrder")
@Tag(name = "CMMS-巡检工单", description = "巡检工单全流程")
public class CmmsInspectionWorkOrderController {

    private final InspectionWorkOrderService inspectionWorkOrderService;

    public CmmsInspectionWorkOrderController(InspectionWorkOrderService inspectionWorkOrderService) {
        this.inspectionWorkOrderService = inspectionWorkOrderService;
    }

    @PostMapping("/create")
    public HxResult<Long> create(@Valid @RequestBody InspectionWorkOrderCreateRequest req) {
        try {
            return HxResult.success(inspectionWorkOrderService.create(req));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/issue")
    public HxResult<?> issue(@Valid @RequestBody InspectionWorkOrderIdRequest req) {
        try {
            inspectionWorkOrderService.issue(req);
            return HxResult.success("下发成功");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/cancel")
    public HxResult<?> cancel(@Valid @RequestBody InspectionWorkOrderIdRequest req) {
        try {
            inspectionWorkOrderService.cancel(req);
            return HxResult.success("已取消");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/page")
    public HxResult<IPage<InspectionWorkOrder>> page(@RequestBody InspectionWorkOrderPageRequest req) {
        return HxResult.success(inspectionWorkOrderService.page(req));
    }

    @PostMapping("/myPage")
    public HxResult<IPage<InspectionWorkOrder>> myPage(@Valid @RequestBody InspectionWorkOrderMyPageRequest req) {
        try {
            return HxResult.success(inspectionWorkOrderService.myPage(req));
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @GetMapping("/detail")
    public HxResult<InspectionWorkOrderFullVO> detail(@RequestParam Long orderId) {
        try {
            return HxResult.success(inspectionWorkOrderService.detailFull(orderId));
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/step/execute")
    public HxResult<?> stepExecute(@Valid @RequestBody InspectionStepExecuteRequest req) {
        try {
            inspectionWorkOrderService.executeStep(req);
            return HxResult.success("步骤已完成");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/step/collect")
    public HxResult<?> stepCollect(@Valid @RequestBody InspectionStepCollectRequest req) {
        try {
            inspectionWorkOrderService.collectStep(req);
            return HxResult.success("采集已保存");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/scanDevice")
    public HxResult<?> scanDevice(@Valid @RequestBody InspectionScanDeviceRequest req) {
        try {
            inspectionWorkOrderService.scanDevice(req);
            return HxResult.success("设备已关联");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }
}
