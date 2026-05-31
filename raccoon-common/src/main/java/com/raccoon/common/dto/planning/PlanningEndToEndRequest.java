package com.raccoon.common.dto.planning;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务规划 Agent 端到端入口：自然语言或结构化参数。
 */
@Data
public class PlanningEndToEndRequest {

    private String requestId;
    private String userInput;
    private Long mapId;
    private String areaName;
    private String taskType;
    private String priority;
    private List<String> deviceNames = new ArrayList<>();
    private Long inspectorId;
    private String inspectorName;
    private String remark;
    private Boolean enableSimulation = Boolean.TRUE;
}
