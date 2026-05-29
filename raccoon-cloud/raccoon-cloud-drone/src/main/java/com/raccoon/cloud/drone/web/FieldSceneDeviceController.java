package com.raccoon.cloud.drone.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceSaveRequest;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceSyncRequest;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceVO;
import com.raccoon.cloud.drone.service.FieldSceneDeviceService;
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
import java.util.Map;

@RestController
@RequestMapping("/field-scene-device")
@RequiredArgsConstructor
public class FieldSceneDeviceController {

    private final FieldSceneDeviceService fieldSceneDeviceService;

    @GetMapping("/page")
    public HxResult<Page<FieldSceneDeviceVO>> page(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "10") long size,
            @RequestParam(value = "mapId", required = false) Long mapId,
            @RequestParam(value = "sceneType", required = false) String sceneType,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return HxResult.success(fieldSceneDeviceService.page(current, size, mapId, sceneType, keyword));
    }

    @GetMapping("/by-map/{mapId}")
    public HxResult<List<FieldSceneDeviceVO>> byMap(@PathVariable Long mapId) {
        return HxResult.success(fieldSceneDeviceService.listByMapId(mapId));
    }

    @GetMapping("/{id}")
    public HxResult<FieldSceneDeviceVO> get(@PathVariable Long id) {
        return HxResult.success(fieldSceneDeviceService.getById(id));
    }

    @PostMapping
    public HxResult<FieldSceneDeviceVO> create(@Valid @RequestBody FieldSceneDeviceSaveRequest request) {
        request.setId(null);
        return HxResult.success(fieldSceneDeviceService.save(request));
    }

    @PutMapping("/{id}")
    public HxResult<FieldSceneDeviceVO> update(
            @PathVariable Long id,
            @Valid @RequestBody FieldSceneDeviceSaveRequest request
    ) {
        request.setId(id);
        return HxResult.success(fieldSceneDeviceService.save(request));
    }

    @DeleteMapping("/{id}")
    public HxResult<Void> delete(@PathVariable Long id) {
        fieldSceneDeviceService.delete(id);
        return HxResult.success(null);
    }

    /** 仿真场景编辑器 → 云端同步 */
    @PostMapping("/sync-from-scene")
    public HxResult<Map<String, Object>> syncFromScene(@Valid @RequestBody FieldSceneDeviceSyncRequest request) {
        int n = fieldSceneDeviceService.syncFromScene(request);
        return HxResult.success(Map.of("upserted", n));
    }

    @PostMapping("/init-builtin-towers")
    public HxResult<Map<String, Object>> initBuiltin() {
        int n = fieldSceneDeviceService.initBuiltinPatrolTowers();
        int backfill = fieldSceneDeviceService.backfillMissingCoordinates(null);
        return HxResult.success(Map.of("initialized", n, "backfilled", backfill));
    }

    /** 按仿真场景几何补全缺失的 WGS84 / 场景坐标 */
    @PostMapping("/backfill-coordinates")
    public HxResult<Map<String, Object>> backfill(
            @RequestParam(value = "mapId", required = false) Long mapId
    ) {
        int n = fieldSceneDeviceService.backfillMissingCoordinates(mapId);
        return HxResult.success(Map.of("backfilled", n));
    }
}
