package com.raccoon.cloud.agent.ai.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 专用 ChatClient：在容器中按名称引用，不影响其它业务的 ChatModel 使用方式。
 */
@Configuration
public class RagChatClientConfiguration {

    public static final String BEAN_NAME = "ragChatClient";

    @Bean(BEAN_NAME)
    public ChatClient ragChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
