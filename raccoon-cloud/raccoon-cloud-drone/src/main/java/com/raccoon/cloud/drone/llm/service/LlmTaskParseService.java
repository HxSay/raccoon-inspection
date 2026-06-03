package com.raccoon.cloud.drone.llm.service;

import com.raccoon.cloud.drone.llm.client.LlmChatClient;
import com.raccoon.cloud.drone.llm.config.LlmTaskParseProperties;
import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;
import com.raccoon.cloud.drone.llm.util.InspectionSlotNormalizer;
import com.raccoon.cloud.drone.llm.util.LlmJsonExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * 调用 LLM 解析任务槽位：超时 5000ms，最大重试 3 次，失败降级 RuleParseService。
 */
@Slf4j
@Service
public class LlmTaskParseService {

    @Autowired
    private LlmChatClient llmChatClient;

    @Autowired
    private LlmPromptBuilder llmPromptBuilder;

    @Autowired
    private RuleParseService ruleParseService;

    @Autowired
    private LlmTaskParseProperties properties;

    @Autowired
    private InspectionSlotNormalizer slotNormalizer;

    public LlmTaskSlotResult parse(String cleanedInput) {
        if (shouldUseRuleFastPath(cleanedInput)) {
            LlmTaskSlotResult rule = ruleParseService.parse(cleanedInput);
            if (hasResolvedTargets(rule)) {
                log.info("规则快速路径命中（杆塔/全量意图），跳过 LLM");
                return rule;
            }
        }
        String prompt = llmPromptBuilder.buildPrompt(cleanedInput);
        int maxRetries = Math.max(1, properties.getMaxRetries());
        Exception lastEx = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String raw = callWithTimeout(prompt);
                LlmTaskSlotResult slots = LlmJsonExtractor.parseSlotJson(raw);
                slots.setParseSource("LLM");
                log.info("LLM 任务解析成功 attempt={}", attempt);
                return slots;
            } catch (Exception e) {
                lastEx = e;
                log.warn("LLM 解析失败 attempt={}/{}: {}", attempt, maxRetries, e.getMessage());
            }
        }

        log.error("LLM 解析全部失败，降级规则解析: {}", lastEx != null ? lastEx.getMessage() : "unknown");
        return ruleParseService.parse(cleanedInput);
    }

    private boolean shouldUseRuleFastPath(String cleanedInput) {
        if (cleanedInput == null || cleanedInput.isBlank()) {
            return false;
        }
        return slotNormalizer.isInspectAllDevicesIntent(cleanedInput)
                || !slotNormalizer.extractTowerDeviceNames(cleanedInput).isEmpty();
    }

    private boolean hasResolvedTargets(LlmTaskSlotResult rule) {
        return rule.getDeviceNames() != null && !rule.getDeviceNames().isEmpty();
    }

    private String callWithTimeout(String prompt) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> llmChatClient.chat(prompt));
            return future.get(properties.getTimeoutMs(), TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
