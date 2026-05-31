package com.raccoon.cloud.drone.dispatch.agent;

import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskRequest;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskResponse;
import com.raccoon.cloud.drone.dispatch.dto.PlanningResult;
import com.raccoon.cloud.drone.dispatch.service.TaskGenerateService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 知识增强任务规划 Agent（步骤 3：Spring AI Tool-Calling 编排入口）。
 * <p>设计取舍：<b>确定性流水线为权威核心</b>——真实分配由 {@link TaskGenerateService} 完成（已含知识增强
 * 优先级、传感器硬约束拍卖、可解释审计）。本 Agent 在其之上提供一层「LLM 工具编排」叙述：
 * 由本地 Ollama 通过 {@link TaskPlanningAgentTools} 自主调用设备/知识/图查询工具，输出可解释的规划说明。
 * LLM 不可用或工具编排失败时自动降级为确定性结果，保证可用性。
 *
 * @author raccoon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeAugmentedPlanningAgent {

    private static final String SYSTEM_PROMPT = """
            你是电网多机巡检的中央任务规划 Agent。在给出调度建议前，你必须借助工具获取真实信息，禁止凭空臆断：
            1) 调用 queryTerminalContext 获取目标场景的终端实时状态与传感器能力；
            2) 调用 searchInspectionKnowledge 检索与任务相关的巡检规程/作业标准；
            3) 如有目标设备ID，调用 queryDeviceKnowledgeGraph 获取设备故障关联与拓扑影响。
            随后用简洁中文输出：①推荐出动的终端类型/数量及原因；②需满足的规程与传感器约束；
            ③高优先级依据（故障/拓扑/规程）；④引用到的规程文件或设备关系。不要编造工具未返回的内容。
            """;

    private final ChatModel chatModel;
    private final TaskPlanningAgentTools taskPlanningAgentTools;
    private final TaskGenerateService taskGenerateService;

    @Value("${raccoon.knowledge.agent-orchestration-enabled:true}")
    private boolean orchestrationEnabled;

    private ChatClient chatClient;

    @PostConstruct
    void init() {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 知识增强的任务分配主入口。
     * <p>先由确定性引擎产出权威分配结果，再叠加 LLM 工具编排的可解释叙述。
     */
    public PlanningResult planAndAssign(DispatchTaskRequest request) {
        PlanningResult result = new PlanningResult();
        DispatchTaskResponse assignment = taskGenerateService.generate(request);
        result.setAssignment(assignment);

        if (!orchestrationEnabled) {
            result.setLlmReasoning(assignment.getAssignReason());
            result.setToolOrchestrated(false);
            return result;
        }
        try {
            String narrative = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request, assignment))
                    .tools(taskPlanningAgentTools)
                    .call()
                    .content();
            if (StringUtils.hasText(narrative)) {
                result.setLlmReasoning(narrative.trim());
                result.setToolOrchestrated(true);
            } else {
                result.setLlmReasoning(assignment.getAssignReason());
            }
        } catch (Exception e) {
            log.warn("[planning-agent] LLM 工具编排失败，降级为确定性结果: {}", e.getMessage());
            result.setLlmReasoning(assignment.getAssignReason());
            result.setToolOrchestrated(false);
        }
        return result;
    }

    private String buildUserPrompt(DispatchTaskRequest request, DispatchTaskResponse assignment) {
        StringBuilder sb = new StringBuilder("巡检调度请求：\n");
        if (StringUtils.hasText(request.getUserInput())) {
            sb.append("- 自然语言指令：").append(request.getUserInput()).append('\n');
        }
        if (request.getMapId() != null) {
            sb.append("- 场景地图ID：").append(request.getMapId()).append('\n');
        }
        if (StringUtils.hasText(request.getAreaName())) {
            sb.append("- 区域：").append(request.getAreaName()).append('\n');
        }
        if (request.getDeviceNames() != null && !request.getDeviceNames().isEmpty()) {
            sb.append("- 目标设备：").append(String.join("、", request.getDeviceNames())).append('\n');
        }
        if (request.getDeviceIds() != null && !request.getDeviceIds().isEmpty()) {
            sb.append("- 目标设备ID：").append(request.getDeviceIds()).append('\n');
        }
        sb.append("\n确定性引擎初步分配：").append(assignment.getAssignReason() == null
                ? "（未分配）" : assignment.getAssignReason());
        sb.append("\n请基于工具返回的真实信息，复核并解释该调度方案的合理性与约束。");
        return sb.toString();
    }
}
