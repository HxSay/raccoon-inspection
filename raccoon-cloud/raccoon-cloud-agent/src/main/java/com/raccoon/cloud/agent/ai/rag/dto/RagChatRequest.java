package com.raccoon.cloud.agent.ai.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RAG 问答请求。
 */
@Data
public class RagChatRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    private String deviceId;

    private Integer topK;
}
