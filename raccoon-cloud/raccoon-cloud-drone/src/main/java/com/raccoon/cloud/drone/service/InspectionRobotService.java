package com.raccoon.cloud.drone.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.drone.dto.InspectionRobotSaveRequest;
import com.raccoon.cloud.drone.dto.InspectionRobotVO;
import com.raccoon.cloud.drone.dto.InspectionSceneVO;

import java.util.List;

public interface InspectionRobotService {

    Page<InspectionRobotVO> page(long current, long size, Long mapId, String robotType, String keyword);

    List<InspectionSceneVO> listScenesWithRobots();

    List<InspectionRobotVO> listByMapId(Long mapId);

    List<InspectionRobotVO> listBySceneType(String sceneType);

    InspectionRobotVO getById(Long id);

    InspectionRobotVO save(InspectionRobotSaveRequest request);

    void delete(Long id);
}
