package com.raccoon.cloud.drone.dispatch.model;

import com.raccoon.cloud.drone.dispatch.enums.DispatchPriorityEnum;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskStatusEnum;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskTypeEnum;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度中枢内部任务模型。
 * <p>{@code TaskParseService} 将原始请求标准化为该模型；后续优先级评估、分配、孪生预演、
 * 路径规划、下发各阶段均在本对象上挂载阶段结果（{@link #priorityScore}、{@link #assignedTerminalId}、
 * {@link #pathPlan} 等），形成可追溯的执行上下文。
 * <p>设计要点：
 * <ul>
 *   <li>保留 {@link #extension} 扩展字段，适配多行业自定义键值</li>
 *   <li>状态机由 {@link DispatchTaskStatusEnum} 表达，便于幂等与重试</li>
 *   <li>设备 ID 自动从巡检范围解析填充，{@link #deviceWaypoints} 不强约束</li>
 * </ul>
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class DispatchInspectionTask {

    /** 调度内部任务 ID（由 TaskGenerateService 生成） */
    private String taskId;

    /** 外部业务请求 ID（幂等用） */
    private String requestId;

    /** 任务类型 */
    private DispatchTaskTypeEnum taskType = DispatchTaskTypeEnum.REGULAR;

    /** 任务优先级档位 */
    private DispatchPriorityEnum priority = DispatchPriorityEnum.NORMAL;

    /** 五维加权评分明细 */
    private TaskPriorityScore priorityScore;

    /** 任务生命周期状态 */
    private DispatchTaskStatusEnum status = DispatchTaskStatusEnum.CREATED;

    /** 所属场景地图 ID */
    private Long mapId;

    /** 巡检区域名称 */
    private String areaName;

    /** 巡检设备名称（原始文本，未做 ID 关联） */
    private List<String> deviceNames = new ArrayList<>();

    /** 自动关联后的设备 ID 列表 */
    private List<Long> deviceIds = new ArrayList<>();

    /** 关联后的设备实体快照，供后续 TSP 排序使用 */
    private List<UavInspectionDevice> resolvedDevices = new ArrayList<>();

    /** 巡检点位（来自任务原始描述，可为空） */
    private List<GeoPoint> deviceWaypoints = new ArrayList<>();

    /** 关键等级 0~1（设备价值/重要性） */
    private double criticalLevel = 0.5;

    /** 风险等级 0~1（环境/作业风险） */
    private double riskLevel = 0.3;

    /** 历史故障率 0~1 */
    private double faultHistoryRate = 0.2;

    /** 距上次巡检的时间间隔（小时） */
    private double inspectionIntervalHours = 24.0;

    /** 计划开始时间（可空，按当前时间立即调度） */
    private LocalDateTime planTime;

    /** 任务截止时间（可空） */
    private LocalDateTime deadline;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 备注（来自用户原始描述） */
    private String remark;

    /** 用户自然语言原文（调度请求 userInput） */
    private String userInput;

    /** 是否「区域内全部设备/杆塔」巡检意图 */
    private Boolean inspectAllDevices;

    /**
     * 规程 / 语义要求的必备传感器（硬约束）。
     * <p>来源：LLM 语义解析（如「热成像复巡」→ THERMAL_IR）+ 知识库规程硬约束。
     * 拍卖分配时不满足该传感器要求的终端将被强制排除。
     */
    private java.util.Set<String> requiredSensors = new java.util.LinkedHashSet<>();

    /** 是否被硬性规程约束阻断（如雨天禁飞），阻断后不再下发 */
    private boolean blocked;

    /** 阻断原因 */
    private String blockReason;

    /** 混合 RAG 知识上下文（步骤 2 构建，可空） */
    private com.raccoon.cloud.drone.dispatch.model.knowledge.KnowledgeContext knowledgeContext;

    /** 分配结果：被分到的终端 ID（未分配为 null） */
    private Long assignedTerminalId;

    /** 拍卖竞拍价 */
    private Double bidFinalPrice;

    /** 分配理由（可解释，分配完成后填充） */
    private String assignReason;

    /** 分配引用的知识片段 ID（Milvus chunkId） */
    private List<String> citedChunkIds = new ArrayList<>();

    /** 分配引用的图关系摘要（Neo4j） */
    private List<String> citedGraphRefs = new ArrayList<>();

    /** 全局路径规划结果 */
    private GlobalPathPlan pathPlan;

    /** 数字孪生预演结果 */
    private SimulationResult simulationResult;

    /** 解析来源 LLM / RULE / MANUAL */
    private String parseSource;

    /** 扩展字段，行业定制化 */
    private Map<String, Object> extension = new HashMap<>();
}
