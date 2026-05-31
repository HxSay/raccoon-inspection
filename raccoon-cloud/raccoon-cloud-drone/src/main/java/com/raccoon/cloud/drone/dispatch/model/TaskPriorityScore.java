package com.raccoon.cloud.drone.dispatch.model;

import com.raccoon.cloud.drone.dispatch.enums.DispatchPriorityEnum;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 任务优先级评分明细：五维加权结果。
 * <p>权重定义见 {@code TaskPriorityService}：故障历史 30%、负载 25%、关键等级 20%、风险 15%、巡检间隔 10%。
 * 所有维度得分归一化到 [0,1]，{@code finalScore} = Σ(维度得分 × 权重)。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class TaskPriorityScore {

    /** 故障历史维度（0~1）：30% */
    private double faultHistoryScore;

    /** 系统负载维度（0~1）：25%，与终端实时负载耦合 */
    private double loadScore;

    /** 关键设备等级维度（0~1）：20% */
    private double criticalLevelScore;

    /** 风险等级维度（0~1）：15% */
    private double riskScore;

    /** 巡检间隔维度（0~1）：10%，距上次巡检越久得分越高 */
    private double intervalScore;

    // ============== 知识增强维度（混合 RAG，无知识命中时为 0） ==============

    /** 五维基础综合得分（0~1），知识增强前的分数 */
    private double baseScore;

    /** 规程约束紧迫度（0~1）：检索片段含「立即复巡/加密巡检」等关键词 */
    private double regulationScore;

    /** 图拓扑影响（0~1）：下游关键设备越多分越高 */
    private double topologyScore;

    /** 故障关联（0~1）：未闭环 / 高 severity 故障 */
    private double faultGraphScore;

    /** 是否启用了知识增强加权 */
    private boolean knowledgeEnhanced;

    /** 加权综合得分（0~1） */
    private double finalScore;

    /** 由 {@link #finalScore} 映射出的优先级档 */
    private DispatchPriorityEnum priorityLevel;
}
