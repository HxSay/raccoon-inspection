package com.raccoon.cloud.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.agent.dto.OllamaChatRequest;
import com.raccoon.cloud.agent.dto.OllamaChatResponse;
import com.raccoon.cloud.agent.dto.OllamaHealthVO;
import com.raccoon.cloud.agent.dto.OllamaModelVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaChatService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String defaultModel;

    @Value("${spring.ai.ollama.chat.options.temperature:0.7}")
    private Double defaultTemperature;

    public OllamaChatResponse chat(OllamaChatRequest request) {
        String model = request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel().trim()
                : defaultModel;
        double temperature = request.getTemperature() != null
                ? request.getTemperature()
                : defaultTemperature;

        OllamaOptions options = OllamaOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();

        long start = System.currentTimeMillis();
        ChatResponse response = chatModel.call(new Prompt(request.getMessage().trim(), options));
        String content = response.getResult().getOutput().getText();
        long elapsed = System.currentTimeMillis() - start;

        log.debug("Ollama chat model={} elapsedMs={}", model, elapsed);
        return new OllamaChatResponse(model, content, elapsed);
    }

    public List<OllamaModelVO> listModels() {
        try {
            String body = restClientBuilder.build()
                    .get()
                    .uri(ollamaBaseUrl + "/api/tags")
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode models = root.path("models");
            if (!models.isArray()) {
                return List.of();
            }
            List<OllamaModelVO> result = new ArrayList<>();
            for (JsonNode node : models) {
                OllamaModelVO vo = new OllamaModelVO();
                vo.setName(node.path("name").asText());
                if (node.has("size")) {
                    vo.setSize(node.path("size").asLong());
                }
                if (node.has("modified_at")) {
                    vo.setModifiedAt(node.path("modified_at").asText());
                }
                if (!vo.getName().isBlank()) {
                    result.add(vo);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("list ollama models failed: {}", e.getMessage());
            throw new IllegalStateException("无法获取 Ollama 模型列表，请确认 Ollama 已启动: " + e.getMessage());
        }
    }

    public OllamaHealthVO health() {
        OllamaHealthVO vo = new OllamaHealthVO();
        vo.setBaseUrl(ollamaBaseUrl);
        vo.setDefaultModel(defaultModel);
        try {
            String body = restClientBuilder.build()
                    .get()
                    .uri(ollamaBaseUrl)
                    .retrieve()
                    .body(String.class);
            vo.setReachable(true);
            vo.setVersion(body != null ? body.trim() : "ok");
            vo.setMessage("Ollama 服务可用");
        } catch (Exception e) {
            vo.setReachable(false);
            vo.setMessage("无法连接 Ollama: " + e.getMessage());
            log.warn("ollama health check failed: {}", e.getMessage());
        }
        return vo;
    }
}
