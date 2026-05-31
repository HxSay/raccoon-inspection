package com.raccoon.cloud.agent.ai.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RAG 纯向量检索请求（供中央 Agent 任务规划构建知识上下文）。
 */
@Data
public class RagSearchRequest {

    @NotBlank(message = "检索词不能为空")
    private String query;

    /** 设备过滤，可空 */
    private String deviceId;

    /** 返回条数，默认 5 */
    private Integer topK = 5;
}
