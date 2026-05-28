package com.raccoon.cloud.drone.dispatch.model;

import com.raccoon.cloud.drone.dispatch.enums.DispatchPriorityEnum;
import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 调度作业清单：下发到边缘执行 Agent 的最终载荷。
 * <p>根据 {@link #terminalType} 差异化封装：UAV 用 {@link UavRouteDispatchPayload}，
 * 机器狗/地面机器人共享同一结构（仅在 algorithm、speed 等元字段上区分）。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class DispatchWorkOrder {

    /** 内部任务 ID */
    private String taskId;

    /** 关联路径规划 ID（数据库主键） */
    private Long planId;

    /** 终端 ID */
    private Long terminalId;

    /** 终端类型 */
    private String terminalType;

    /** 任务类型 */
    private String taskTypeCode;

    /** 优先级 */
    private DispatchPriorityEnum priority;

    /** 标准化下发 JSON（与现有 uav_route_plan 下发结构对齐） */
    private UavRouteDispatchPayload payload;

    /** 下发时间 */
    private LocalDateTime dispatchTime;

    /** 下发结果 SUCCESS/FAILED/PENDING */
    private String dispatchResult;

    /** 错误信息（dispatchResult=FAILED 时） */
    private String errorMessage;
}
