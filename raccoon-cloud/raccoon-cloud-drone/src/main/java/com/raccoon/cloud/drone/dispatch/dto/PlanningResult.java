package com.raccoon.cloud.drone.dispatch.dto;

import lombok.Data;

/**
 * 知识增强任务规划结果（Tool-Calling 编排入口返回）。
 * <p>权威分配结果由确定性调度引擎产出（{@link DispatchTaskResponse}）；
 * {@code llmReasoning} 为本地 LLM 通过工具调用编排得到的可解释规划叙述。
 *
 * @author raccoon
 */
@Data
public class PlanningResult {

    /** 确定性调度引擎的权威分配结果 */
    private DispatchTaskResponse assignment;

    /** LLM 工具编排生成的规划叙述（可空，失败时降级为分配理由） */
    private String llmReasoning;

    /** 是否真正经过 LLM 工具编排（false=LLM不可用，已降级为纯确定性） */
    private boolean toolOrchestrated;
}
