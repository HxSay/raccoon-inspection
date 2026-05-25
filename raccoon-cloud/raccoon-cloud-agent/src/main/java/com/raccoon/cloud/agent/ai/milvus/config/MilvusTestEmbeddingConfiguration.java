package com.raccoon.cloud.agent.ai.milvus.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 测试专用 Embedding：独立 Bean。
 * 使用 @Primary 解决 raccoon-cloud-agent 中存在多个 EmbeddingModel 时的歧义，
 * 同时保证 Spring AI 自动装配的 vectorStore 使用此模型（虽然测试代码不直接引用它）。
 * 测试逻辑通过 @Qualifier(BEAN_NAME) 显式注入此 Bean，与 Spring AI 默认行为完全独立。
 */
@Slf4j
@Configuration
@ConditionalOnClass(OllamaEmbeddingModel.class)
public class MilvusTestEmbeddingConfiguration {

    public static final String BEAN_NAME = "milvusTestEmbeddingModel";

    @Bean(name = BEAN_NAME)
    @Primary
    @ConditionalOnMissingBean(name = BEAN_NAME)
    public EmbeddingModel milvusTestEmbeddingModel(
            OllamaApi ollamaApi,
            @Value("${raccoon.milvus-test.embedding-model:nomic-embed-text}") String model,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        log.info("Milvus 测试：构建 Embedding 模型 {} (base-url={})", model, baseUrl);
        OllamaOptions options = OllamaOptions.builder().model(model).build();
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
    }
}
