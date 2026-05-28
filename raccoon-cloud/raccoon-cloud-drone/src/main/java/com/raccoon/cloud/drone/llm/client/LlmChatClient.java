package com.raccoon.cloud.drone.llm.client;

/**
 * LLM 调用客户端抽象。
 */
public interface LlmChatClient {

    /**
     * @param prompt 完整 Prompt
     * @return 模型原始文本
     */
    String chat(String prompt) throws Exception;
}
