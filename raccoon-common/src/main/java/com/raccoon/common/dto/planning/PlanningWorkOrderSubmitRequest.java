package com.raccoon.common.dto.planning;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 中央任务规划 Agent 完成调度后，提交至 CMMS 生成巡检工单（待审核）的请求体。
 */
@Data
public class PlanningWorkOrderSubmitRequest {

    /** 调度内部任务 ID（drone） */
    private String dispatchTaskId;

    private String requestId;

    private Long mapId;

    private String areaName;

    /** REGULAR / EMERGENCY / RE_INSPECTION 等 */
    private String taskTypeCode;

    /** NORMAL / HIGH / URGENT / CRITICAL */
    private String priorityCode;

    private String userInput;

    private String remark;

    private Long assignedTerminalId;

    private String assignedTerminalName;

    private String assignReason;

    /** 路径规划摘要 JSON（可选） */
    private String pathPlanJson;

    /** 待巡检设备名称列表 */
    private List<String> deviceNames = new ArrayList<>();

    /** 默认现场负责人（审核人）用户 ID */
    private Long inspectorId;

    private String inspectorName;
}
