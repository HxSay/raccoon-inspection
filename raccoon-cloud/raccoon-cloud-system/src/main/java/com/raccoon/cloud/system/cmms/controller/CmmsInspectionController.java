package com.raccoon.cloud.system.cmms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.cmms.dto.InspectionPlanPageRequest;
import com.raccoon.cloud.system.cmms.dto.InspectionTaskPageRequest;
import com.raccoon.cloud.system.cmms.dto.TaskCheckInRequest;
import com.raccoon.cloud.system.cmms.dto.TaskCompleteRequest;
import com.raccoon.cloud.system.cmms.dto.TaskReportAbnormalRequest;
import com.raccoon.cloud.system.cmms.entity.InspectionPlan;
import com.raccoon.cloud.system.cmms.entity.InspectionRecord;
import com.raccoon.cloud.system.cmms.entity.InspectionTask;
import com.raccoon.cloud.system.cmms.service.InspectionPlanService;
import com.raccoon.cloud.system.cmms.service.InspectionRecordService;
import com.raccoon.cloud.system.cmms.service.InspectionTaskService;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 巡检计划、任务、记录。
 */
@RestController
@RequestMapping("/cmms/inspection")
@Tag(name = "CMMS-巡检管理", description = "计划、任务、记录")
public class CmmsInspectionController {

    private final InspectionPlanService inspectionPlanService;
    private final InspectionTaskService inspectionTaskService;
    private final InspectionRecordService inspectionRecordService;

    public CmmsInspectionController(InspectionPlanService inspectionPlanService,
                                    InspectionTaskService inspectionTaskService,
                                    InspectionRecordService inspectionRecordService) {
        this.inspectionPlanService = inspectionPlanService;
        this.inspectionTaskService = inspectionTaskService;
        this.inspectionRecordService = inspectionRecordService;
    }

    @PostMapping("/plan/page")
    public HxResult<IPage<InspectionPlan>> planPage(@RequestBody InspectionPlanPageRequest req) {
        return HxResult.success(inspectionPlanService.page(req));
    }

    @PostMapping("/plan/save")
    public HxResult<?> planSave(@RequestBody InspectionPlan body) {
        try {
            inspectionPlanService.save(body);
            return HxResult.success("保存成功");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/plan/delete")
    public HxResult<?> planDelete(@RequestParam Long id) {
        inspectionPlanService.delete(id);
        return HxResult.success("删除成功");
    }

    @PostMapping("/plan/generateTasks")
    @Operation(summary = "按计划生成巡检任务")
    public HxResult<Integer> planGenerateTasks(@RequestParam Long planId) {
        try {
            int n = inspectionTaskService.generateTasksFromPlan(planId);
            return HxResult.success("已生成 " + n + " 条任务", n);
        } catch (Exception e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/task/page")
    public HxResult<IPage<InspectionTask>> taskPage(@RequestBody InspectionTaskPageRequest req) {
        return HxResult.success(inspectionTaskService.page(req));
    }

    @GetMapping("/task/get")
    public HxResult<InspectionTask> taskGet(@RequestParam Long id) {
        return HxResult.success(inspectionTaskService.get(id));
    }

    @PostMapping("/task/save")
    public HxResult<?> taskSave(@RequestBody InspectionTask body) {
        inspectionTaskService.save(body);
        return HxResult.success("保存成功");
    }

    @PostMapping("/task/delete")
    public HxResult<?> taskDelete(@RequestParam Long id) {
        inspectionTaskService.delete(id);
        return HxResult.success("删除成功");
    }

    @PostMapping("/task/complete")
    @Operation(summary = "提交巡检结果并完成任务")
    public HxResult<?> taskComplete(@RequestBody TaskCompleteRequest body) {
        try {
            inspectionTaskService.completeTask(body);
            return HxResult.success("任务已完成");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/task/checkIn")
    @Operation(summary = "GPS签到", description = "写入任务经纬度，可选扫码校验设备编号")
    public HxResult<?> taskCheckIn(@RequestBody TaskCheckInRequest body) {
        try {
            inspectionTaskService.checkIn(body.getTaskId(), body.getLongitude(), body.getLatitude(), body.getScannedDeviceCode());
            return HxResult.success("签到成功");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/task/start")
    @Operation(summary = "开始执行任务", description = "状态 0→1")
    public HxResult<?> taskStart(@RequestParam Long taskId) {
        try {
            inspectionTaskService.startTask(taskId);
            return HxResult.success("已开始执行");
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @PostMapping("/task/reportAbnormal")
    @Operation(summary = "异常上报并生成维修工单")
    public HxResult<Long> taskReportAbnormal(@RequestBody TaskReportAbnormalRequest body) {
        try {
            Long woId = inspectionTaskService.reportAbnormal(body.getTaskId(), body.getFaultDescription(), body.getFaultImageUrls());
            return HxResult.success("已生成工单", woId);
        } catch (IllegalArgumentException e) {
            return HxResult.fail(e.getMessage());
        }
    }

    @GetMapping("/record/list")
    public HxResult<List<InspectionRecord>> recordList(@RequestParam Long taskId) {
        return HxResult.success(inspectionRecordService.listByTask(taskId));
    }
}
