package com.raccoon.cloud.drone.fault.service;

import com.raccoon.cloud.drone.fault.dto.FaultGradingContext;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import com.raccoon.cloud.drone.llm.client.LlmChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 规则引擎 + 综合评分 + 可选 LLM 边界 refine。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaultGradingEngine {

    private static final Set<String> CRITICAL_TYPES = Set.of(
            "FIRE", "SMOKE", "GAS_LEAK", "ARC_DISCHARGE", "DISASTER");

    private final FaultGradingKnowledgeService knowledgeService;

    @Autowired(required = false)
    private LlmChatClient llmChatClient;

    public FaultLevel grade(FaultEvent event) {
        FaultGradingContext ctx = knowledgeService.buildContext(event);
        FaultLevel ruleLevel = applyHardRules(event);
        if (ruleLevel == FaultLevel.CRITICAL) {
            event.setSeverityScore(1.0);
            return FaultLevel.CRITICAL;
        }
        double score = calculateSeverityScore(event, ctx);
        if (score >= 0.45 && score <= 0.75) {
            score = refineScoreByLlm(event, ctx, score);
        }
        event.setSeverityScore(score);
        if (score >= 0.85) {
            return FaultLevel.CRITICAL;
        }
        if (score >= 0.55) {
            return FaultLevel.SERIOUS;
        }
        return FaultLevel.GENERAL;
    }

    private FaultLevel applyHardRules(FaultEvent event) {
        if (event.getFaultType() != null && CRITICAL_TYPES.contains(event.getFaultType().trim().toUpperCase())) {
            return FaultLevel.CRITICAL;
        }
        Double conf = event.getConfidence();
        if (conf != null && conf >= 0.95 && "TEMP_ABNORMAL".equalsIgnoreCase(event.getFaultType())) {
            Map<String, Object> ext = event.getExtData();
            if (ext != null) {
                Object maxTemp = ext.get("maxTemp");
                double temp = maxTemp instanceof Number n ? n.doubleValue() : 0.0;
                if (temp > 120.0) {
                    return FaultLevel.CRITICAL;
                }
            }
        }
        return null;
    }

    private double calculateSeverityScore(FaultEvent event, FaultGradingContext ctx) {
        double conf = event.getConfidence() != null ? event.getConfidence() : 0.5;
        double score = conf * 0.35;
        score += ctx.getCriticalLevelScore() * 0.25;
        score += ctx.getDownstreamImpactScore() * 0.20;
        score += ctx.getOpenFaultScore() * 0.10;
        score += ctx.getRegulationUrgencyScore() * 0.10;
        return Math.min(score, 1.0);
    }

    private double refineScoreByLlm(FaultEvent event, FaultGradingContext ctx, double score) {
        if (llmChatClient == null) {
            return score;
        }
        try {
            String prompt = "你是电力巡检故障定级助手。根据以下信息输出0~1的严重度分数（仅数字）：\n"
                    + "故障类型:" + event.getFaultType() + "\n"
                    + "描述:" + event.getDescription() + "\n"
                    + "当前规则分:" + score + "\n"
                    + "下游设备数:" + ctx.getTopologyNodes().size();
            String raw = llmChatClient.chat(prompt);
            if (raw != null) {
                String num = raw.replaceAll("[^0-9.]", "").trim();
                if (!num.isEmpty()) {
                    double llm = Double.parseDouble(num);
                    return (score * 0.6) + (Math.min(1.0, llm) * 0.4);
                }
            }
        } catch (Exception e) {
            log.debug("[fault-grade] LLM refine 跳过: {}", e.getMessage());
        }
        return score;
    }
}
