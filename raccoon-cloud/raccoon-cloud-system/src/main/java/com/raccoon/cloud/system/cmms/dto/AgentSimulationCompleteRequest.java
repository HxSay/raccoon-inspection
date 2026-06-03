package com.raccoon.cloud.system.cmms.dto;

import com.raccoon.common.dto.planning.SimulationMissionReportDTO;
import lombok.Data;

/**
 * 仿真/无人机巡检结束后，回写 CMMS 工单与任务状态。
 */
@Data
public class AgentSimulationCompleteRequest {

    private Long workOrderId;
    /** drone 调度任务 ID，与 inspection_work_order.dispatch_task_id 对应 */
    private String dispatchTaskId;
    private String remark;
    /** 仿真任务完成载荷：拍照、多模态采样、AI 检测结果 */
    private SimulationMissionReportDTO missionReport;
}
