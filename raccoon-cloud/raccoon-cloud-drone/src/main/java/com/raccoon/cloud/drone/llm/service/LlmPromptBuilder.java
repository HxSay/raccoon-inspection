package com.raccoon.cloud.drone.llm.service;

import org.springframework.stereotype.Service;

/**
 * Prompt 工程构建：系统提示 + Few-Shot + 用户输入。
 */
@Service
public class LlmPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是电力巡检任务解析专家。请根据用户自然语言，提取巡检任务槽位并输出严格 JSON（不要 markdown 说明）。
            固定字段：
            - taskType: REGULAR | RE_INSPECTION | TEMP
            - areaName: 巡检区域/场景名称（字符串）
            - deviceNames: 设备名称数组
            - priority: NORMAL | URGENT
            - planTime: 计划时间描述（可为空字符串）
            - remark: 备注（可为空字符串）
            只输出一个 JSON 对象，不要其它文字。
            """;

    /** Few-Shot 示例 1 */
    private static final String FEW_SHOT_1_USER = "明天上午对输电线路场景的杆塔1和杆塔2做例行巡检";
    private static final String FEW_SHOT_1_ASSISTANT = """
            {"taskType":"REGULAR","areaName":"输电线路巡检场景","deviceNames":["杆塔1","杆塔2"],"priority":"NORMAL","planTime":"明天上午","remark":"例行巡检"}
            """;

    /** Few-Shot 示例 2 */
    private static final String FEW_SHOT_2_USER = "紧急复巡变电站场景的主变压器和断路器";
    private static final String FEW_SHOT_2_ASSISTANT = """
            {"taskType":"RE_INSPECTION","areaName":"变电站场景","deviceNames":["主变压器","断路器"],"priority":"URGENT","planTime":"","remark":"紧急复巡"}
            """;

    /** Few-Shot 示例 3 */
    private static final String FEW_SHOT_3_USER = "临时检查一下热力管网场景的阀门";
    private static final String FEW_SHOT_3_ASSISTANT = """
            {"taskType":"TEMP","areaName":"热力管网场景","deviceNames":["阀门"],"priority":"NORMAL","planTime":"","remark":"临时检查"}
            """;

    public String buildPrompt(String userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT).append("\n\n");
        sb.append("【示例1】\n用户：").append(FEW_SHOT_1_USER).append("\n输出：").append(FEW_SHOT_1_ASSISTANT).append("\n");
        sb.append("【示例2】\n用户：").append(FEW_SHOT_2_USER).append("\n输出：").append(FEW_SHOT_2_ASSISTANT).append("\n");
        sb.append("【示例3】\n用户：").append(FEW_SHOT_3_USER).append("\n输出：").append(FEW_SHOT_3_ASSISTANT).append("\n");
        sb.append("【当前用户输入】\n用户：").append(userInput).append("\n输出：");
        return sb.toString();
    }
}
