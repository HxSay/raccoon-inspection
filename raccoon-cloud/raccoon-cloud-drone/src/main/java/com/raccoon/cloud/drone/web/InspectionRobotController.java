package com.raccoon.cloud.drone.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.drone.dto.InspectionRobotRuntimeVO;
import com.raccoon.cloud.drone.dto.InspectionRobotSaveRequest;
import com.raccoon.cloud.drone.dto.InspectionRobotTelemetryReport;
import com.raccoon.cloud.drone.dto.InspectionRobotVO;
import com.raccoon.cloud.drone.dto.InspectionSceneVO;
import com.raccoon.cloud.drone.service.InspectionRobotRuntimeService;
import com.raccoon.cloud.drone.service.InspectionRobotService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inspection-robot")
@RequiredArgsConstructor
public class InspectionRobotController {

    private final InspectionRobotService inspectionRobotService;
    private final InspectionRobotRuntimeService inspectionRobotRuntimeService;

    @GetMapping("/page")
    public HxResult<Page<InspectionRobotVO>> page(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "mapId", required = false) Long mapId,
            @RequestParam(value = "robotType", required = false) String robotType,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return HxResult.success(inspectionRobotService.page(current, size, mapId, robotType, keyword));
    }

    @GetMapping("/scenes")
    public HxResult<List<InspectionSceneVO>> scenes() {
        return HxResult.success(inspectionRobotService.listScenesWithRobots());
    }

    @GetMapping("/by-map/{mapId}")
    public HxResult<List<InspectionRobotVO>> byMap(@PathVariable Long mapId) {
        return HxResult.success(inspectionRobotService.listByMapId(mapId));
    }

    @GetMapping("/by-scene-type")
    public HxResult<List<InspectionRobotVO>> bySceneType(@RequestParam String sceneType) {
        return HxResult.success(inspectionRobotService.listBySceneType(sceneType));
    }

    @GetMapping("/{id}")
    public HxResult<InspectionRobotVO> get(@PathVariable Long id) {
        return HxResult.success(inspectionRobotService.getById(id));
    }

    @PostMapping
    public HxResult<InspectionRobotVO> create(@Valid @RequestBody InspectionRobotSaveRequest request) {
        request.setId(null);
        return HxResult.success(inspectionRobotService.save(request));
    }

    @PutMapping("/{id}")
    public HxResult<InspectionRobotVO> update(
            @PathVariable Long id,
            @Valid @RequestBody InspectionRobotSaveRequest request
    ) {
        request.setId(id);
        return HxResult.success(inspectionRobotService.save(request));
    }

    @DeleteMapping("/{id}")
    public HxResult<Void> delete(@PathVariable Long id) {
        inspectionRobotService.delete(id);
        return HxResult.success(null);
    }

    /** 边缘/仿真上报实时状态（位置10Hz、电量/负载1Hz、运行10Hz 由终端分 channel 上报） */
    @PostMapping("/telemetry")
    public HxResult<Void> reportTelemetry(@Valid @RequestBody InspectionRobotTelemetryReport report) {
        inspectionRobotRuntimeService.report(report);
        return HxResult.success(null);
    }

    @PostMapping("/telemetry/batch")
    public HxResult<Void> reportTelemetryBatch(@Valid @RequestBody List<InspectionRobotTelemetryReport> reports) {
        inspectionRobotRuntimeService.reportBatch(reports);
        return HxResult.success(null);
    }

    @GetMapping("/{id}/runtime")
    public HxResult<InspectionRobotRuntimeVO> getRuntime(@PathVariable Long id) {
        InspectionRobotVO robot = inspectionRobotService.getById(id);
        return HxResult.success(robot.getRuntime());
    }
}
