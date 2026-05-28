package com.raccoon.cloud.drone.llm.dto;

import com.raccoon.cloud.drone.llm.model.InspectionTask;
import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;
import lombok.Data;

/**
 * NLP 任务解析响应：成功时带任务 JSON；缺槽位时返回追问。
 */
@Data
public class NlpTaskParseResponse {

    /** 是否需追问补全槽位 */
    private boolean needFollowUp;

    /** 追问话术 */
    private String followUpQuestion;

    /** 槽位中间结果（调试） */
    private LlmTaskSlotResult slots;

    /** 标准化任务 JSON */
    private InspectionTask task;

    /** 解析来源 LLM / RULE */
    private String parseSource;
}
