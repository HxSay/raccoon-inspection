package com.raccoon.cloud.drone.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InspectionSceneVO {

    private Long id;
    private String mapName;
    private String sceneType;
    private String remark;
    private int robotCount;
    private List<InspectionRobotVO> robots = new ArrayList<>();
}
