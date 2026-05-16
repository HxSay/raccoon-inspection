package com.raccoon.cloud.drone.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.drone.dto.RoutePlanCreateRequest;
import com.raccoon.cloud.drone.dto.RoutePlanView;
import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import com.raccoon.cloud.drone.entity.UavRoutePlan;
import com.raccoon.cloud.drone.service.UavRoutePlanService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/route-plan")
@RequiredArgsConstructor
public class UavRoutePlanController {

    private final UavRoutePlanService uavRoutePlanService;

    @PostMapping
    public HxResult<RoutePlanView> create(@Valid @RequestBody RoutePlanCreateRequest request) {
        return HxResult.success(uavRoutePlanService.planAndSave(request));
    }

    /**
     * 按无人机 ID + 路径规划 ID（planId）获取下发 JSON（用于联调/仿真拉取）
     */
    @GetMapping("/dispatch")
    public HxResult<UavRouteDispatchPayload> getDispatchByUavAndPlan(
            @RequestParam("uavId") Long uavId,
            @RequestParam("planId") Long planId
    ) {
        return HxResult.success(uavRoutePlanService.getDispatchByUavAndPlan(uavId, planId));
    }

    @GetMapping("/{id}")
    public HxResult<RoutePlanView> getById(@PathVariable("id") Long id) {
        UavRoutePlan one = uavRoutePlanService.getById(id);
        return one == null ? HxResult.fail("记录不存在") : HxResult.success(uavRoutePlanService.toView(one));
    }

    @GetMapping("/page")
    public HxResult<Page<UavRoutePlan>> page(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "uavId", required = false) Long uavId,
            @RequestParam(value = "taskId", required = false) Long taskId
    ) {
        LambdaQueryWrapper<UavRoutePlan> w = new LambdaQueryWrapper<>();
        w.eq(uavId != null, UavRoutePlan::getUavId, uavId);
        w.eq(taskId != null, UavRoutePlan::getTaskId, taskId);
        w.orderByDesc(UavRoutePlan::getId);
        return HxResult.success(uavRoutePlanService.page(new Page<>(current, size), w));
    }
}
