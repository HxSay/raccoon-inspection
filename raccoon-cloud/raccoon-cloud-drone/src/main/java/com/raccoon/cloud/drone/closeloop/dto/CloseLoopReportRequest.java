package com.raccoon.cloud.drone.closeloop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 巡检任务闭环受理入参（边缘/仿真任务完成后上报）。
 * <p>承载「计划快照」与「实际执行结果」，供完整性核对与闭环判定。
 */
@Data
public class CloseLoopReportRequest {

    /** 调度任务 ID（dispatchTaskId） */
    @NotBlank(message = "taskId 不能为空")
    private String taskId;

    private Long mapId;
    private Long workOrderId;
    private String taskName;

    /** —— 计划快照 —— */
    /** 计划巡检设备 ID（按访问顺序） */
    private List<Long> plannedDeviceIds = new ArrayList<>();
    /** 计划拍照航点数 */
    private Integer plannedWaypointCount;
    /** 要求采集的多模态类型，默认 [VISIBLE] */
    private List<String> requiredDataTypes = new ArrayList<>();

    /** —— 实际执行结果 —— */
    /** 实际完成的拍照航点数 */
    private Integer finishedWaypointCount;
    /** 实际完成的点位明细 */
    private List<CapturedPointDTO> capturedPoints = new ArrayList<>();
    /** 各终端执行摘要（多机协同时） */
    private List<TerminalTaskResultDTO> terminals = new ArrayList<>();

    private Double flightDistanceM;
    private Double durationSec;
    private Integer telemetrySent;
    private Boolean multimodalUploaded;

    /** 演示/调试：跳过未完成项自动补检（仅做核对与报告） */
    private Boolean skipReschedule;
    /** 已补检轮次（补检任务回流时透传，用于防止无限补检） */
    private Integer rescheduleCount;
    /** 父任务 ID（补检任务回流闭环时填写） */
    private String parentTaskId;
}
