package com.raccoon.cloud.drone.llm.service;

import com.raccoon.cloud.drone.llm.dto.NlpTaskParseResponse;
import com.raccoon.cloud.drone.llm.model.InspectionTask;
import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;
import com.raccoon.cloud.drone.llm.service.ResultCheckAndFillService.CheckResult;
import com.raccoon.cloud.drone.llm.util.InspectionSlotNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * NLP 任务解析编排：预处理 → LLM/规则解析 → 校验补全 → 转内部任务 JSON。
 */
@Slf4j
@Service
public class NlpTaskParseFacadeService {

    @Autowired
    private LlmInputPreprocessService llmInputPreprocessService;

    @Autowired
    private LlmTaskParseService llmTaskParseService;

    @Autowired
    private ResultCheckAndFillService resultCheckAndFillService;

    @Autowired
    private LlmResultConvertService llmResultConvertService;

    @Autowired
    private InspectionSlotNormalizer slotNormalizer;

    public NlpTaskParseResponse parse(String rawUserInput) {
        String cleaned = llmInputPreprocessService.preprocess(rawUserInput);
        log.info("开始 NLP 任务解析，输入长度={}", cleaned.length());

        LlmTaskSlotResult slots = llmTaskParseService.parse(cleaned);
        slotNormalizer.enrich(cleaned, slots);
        CheckResult check = resultCheckAndFillService.checkAndFill(slots, cleaned);

        NlpTaskParseResponse response = new NlpTaskParseResponse();
        response.setSlots(check.getSlots());
        response.setParseSource(check.getSlots().getParseSource());

        if (check.isNeedFollowUp()) {
            response.setNeedFollowUp(true);
            response.setFollowUpQuestion(check.getFollowUpQuestion());
            log.info("NLP 解析需追问: {}", check.getFollowUpQuestion());
            return response;
        }

        InspectionTask task = llmResultConvertService.convert(check);
        response.setNeedFollowUp(false);
        response.setTask(task);
        log.info("NLP 任务解析完成 parseSource={}", response.getParseSource());
        return response;
    }
}
