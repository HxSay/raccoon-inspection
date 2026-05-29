package com.raccoon.cloud.drone.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceSaveRequest;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceSyncRequest;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceVO;

import java.util.List;

public interface FieldSceneDeviceService {

    Page<FieldSceneDeviceVO> page(long current, long size, Long mapId, String sceneType, String keyword);

    List<FieldSceneDeviceVO> listByMapId(Long mapId);

    FieldSceneDeviceVO getById(Long id);

    FieldSceneDeviceVO save(FieldSceneDeviceSaveRequest request);

    void delete(Long id);

    /** 仿真场景编辑器物体批量同步 */
    int syncFromScene(FieldSceneDeviceSyncRequest request);

    /** 初始化输电场景内置杆塔坐标 */
    int initBuiltinPatrolTowers();

    /** 为缺少坐标的现场设备按仿真场景几何回填 */
    int backfillMissingCoordinates(Long mapId);
}
