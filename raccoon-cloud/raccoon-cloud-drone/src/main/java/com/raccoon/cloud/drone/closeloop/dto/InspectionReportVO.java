package com.raccoon.cloud.drone.closeloop.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检结构化报告（步骤3 自动生成）。
 */
@Data
public class InspectionReportVO {

    private String taskId;
    private String taskName;

    private int terminalCount;
    private double totalDistanceM;
    private Double durationSec;

    private int plannedWaypointCount;
    private int finishedWaypointCount;
    private int missedDeviceCount;
    private int dataGapCount;
    private int defectCount;

    private double completionRate;
    private boolean passed;
    private String closeStatus;

    /** 执行摘要（规则模板生成；可后续替换为 LLM） */
    private String executiveSummary;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generateTime;
}
