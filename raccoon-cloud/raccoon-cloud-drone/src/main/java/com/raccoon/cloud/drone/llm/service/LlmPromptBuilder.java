package com.raccoon.cloud.drone.llm.service;

import com.raccoon.cloud.drone.llm.catalog.InspectionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Prompt 工程构建：系统提示 + 系统内真实场景清单 + Few-Shot + 用户输入。
 */
@Service
@RequiredArgsConstructor
public class LlmPromptBuilder {

    private final InspectionCatalogService catalogService;

    private static final String SYSTEM_PROMPT = """
            你是电力巡检任务解析专家。请根据用户自然语言，提取巡检任务槽位并输出严格 JSON（不要 markdown 说明）。
            固定字段：
            - taskType: REGULAR | RE_INSPECTION | TEMP
            - areaName: 巡检区域/场景名称；必须从下方「可选场景」列表中选择语义最匹配的一项，并原样输出，不要自创名称
            - deviceNames: 设备名称数组；杆塔类设备统一用「杆塔+阿拉伯数字编号」格式（必须用真实数字，禁止输出占位符）。例如：用户说“4号塔杆/4号杆塔/塔杆4/第4基杆塔”都输出 "杆塔4"；“1号和3号塔杆”输出 ["杆塔1","杆塔3"]
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

    /** Few-Shot 示例 4：编号在前的口语化杆塔名 → 规范为「杆塔N」 */
    private static final String FEW_SHOT_4_USER = "帮我巡检电网巡检场景的4号塔杆";
    private static final String FEW_SHOT_4_ASSISTANT = """
            {"taskType":"REGULAR","areaName":"输电线路巡检场景","deviceNames":["杆塔4"],"priority":"NORMAL","planTime":"","remark":""}
            """;

    public String buildPrompt(String userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_PROMPT).append("\n");
        sb.append("【可选场景】（areaName 只能取其一，原样输出）：\n");
        List<String> areas = catalogService.listAllAreaNames();
        if (areas.isEmpty()) {
            sb.append("- 输电线路巡检场景\n- 变电站场景\n- 热力管网场景\n");
        } else {
            for (String a : areas) {
                sb.append("- ").append(a).append("\n");
            }
        }
        sb.append("\n");
        sb.append("【示例1】\n用户：").append(FEW_SHOT_1_USER).append("\n输出：").append(FEW_SHOT_1_ASSISTANT).append("\n");
        sb.append("【示例2】\n用户：").append(FEW_SHOT_2_USER).append("\n输出：").append(FEW_SHOT_2_ASSISTANT).append("\n");
        sb.append("【示例3】\n用户：").append(FEW_SHOT_3_USER).append("\n输出：").append(FEW_SHOT_3_ASSISTANT).append("\n");
        sb.append("【示例4】\n用户：").append(FEW_SHOT_4_USER).append("\n输出：").append(FEW_SHOT_4_ASSISTANT).append("\n");
        sb.append("【当前用户输入】\n用户：").append(userInput).append("\n输出：");
        return sb.toString();
    }
}
