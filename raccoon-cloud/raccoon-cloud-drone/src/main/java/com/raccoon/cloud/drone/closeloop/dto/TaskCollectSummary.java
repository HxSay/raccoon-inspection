package com.raccoon.cloud.drone.closeloop.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 多机结果回收聚合摘要（步骤1 输出）。
 */
@Data
public class TaskCollectSummary {

    private String taskId;
    /** 是否所有终端均已上报（false 表示超时强制核对） */
    private boolean allTerminalReported;
    private int terminalCount;

    private int plannedWaypointCount;
    private int finishedWaypointCount;
    private double totalDistanceM;
    private Double durationSec;

    private Set<Long> allFinishedDeviceIds = new LinkedHashSet<>();
    private List<CapturedPointDTO> capturedPoints = new ArrayList<>();
}
