package com.raccoon.cloud.drone.dispatch.dto;

import com.raccoon.cloud.drone.dispatch.enums.DispatchPriorityEnum;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskStatusEnum;
import com.raccoon.cloud.drone.dispatch.model.BidPrice;
import com.raccoon.cloud.drone.dispatch.model.ConflictInfo;
import com.raccoon.cloud.drone.dispatch.model.DispatchWorkOrder;
import com.raccoon.cloud.drone.dispatch.model.GlobalPathPlan;
import com.raccoon.cloud.drone.dispatch.model.TaskPriorityScore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 调度中枢统一返回 VO。
 *
 * @author raccoon
 */
@Data
public class DispatchTaskResponse {

    /** 内部任务 ID */
    private String taskId;

    /** 外部请求 ID */
    private String requestId;

    /** 任务类型 */
    private String taskTypeCode;

    /** 优先级 */
    private DispatchPriorityEnum priority;

    /** 五维评分明细 */
    private TaskPriorityScore priorityScore;

    /** 任务状态 */
    private DispatchTaskStatusEnum status;

    /** 是否分配成功 */
    private boolean assigned;

    /** 分配到的终端 ID */
    private Long assignedTerminalId;

    /** 分配终端名称 */
    private String assignedTerminalName;

    /** 拍卖竞拍价 */
    private Double bidPrice;

    /** 分配理由（可解释，融合实时状态/传感器/知识约束） */
    private String assignReason;

    /** 中标终端竞拍价明细 */
    private BidPrice bidBreakdown;

    /** 全部候选终端竞拍价矩阵（可解释/审计） */
    private List<BidPrice> bidMatrix = new ArrayList<>();

    /** 任务所需传感器（规程/语义硬约束） */
    private List<String> requiredSensors = new ArrayList<>();

    /** 是否被规程硬约束阻断 */
    private boolean blocked;

    /** 阻断原因 */
    private String blockReason;

    /** 分配引用的知识片段 ID（Milvus chunkId） */
    private List<String> citedChunkIds = new ArrayList<>();

    /** 分配引用的图关系摘要（Neo4j） */
    private List<String> citedGraphRefs = new ArrayList<>();

    /** 全局路径规划摘要 */
    private GlobalPathPlan pathPlan;

    /** 数字孪生预演检出冲突 */
    private List<ConflictInfo> conflicts = new ArrayList<>();

    /** 作业清单 */
    private DispatchWorkOrder workOrder;

    /** 端到端处理耗时（ms） */
    private long totalElapsedMs;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 提示信息 */
    private String message;
}
