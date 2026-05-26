package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * RAG 问答结果：回答 + 引用 + 设备上下文。
 */
@Data
@Builder
public class RagChatResponse {

    private String answer;

    private List<RagReferenceVO> references;

    private RagDeviceContextVO deviceContext;

    private Long elapsedMs;

    private String model;
}
