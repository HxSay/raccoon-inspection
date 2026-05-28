package com.raccoon.cloud.drone.llm.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OllamaLlmChatClient implements LlmChatClient {

    @Autowired
    private ChatModel chatModel;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String model;

    @Value("${spring.ai.ollama.chat.options.temperature:0.1}")
    private Double temperature;

    @Override
    public String chat(String prompt) throws Exception {
        OllamaOptions options = OllamaOptions.builder()
                .model(model)
                .temperature(temperature)
                .build();
        ChatResponse response = chatModel.call(new Prompt(prompt, options));
        String text = response.getResult().getOutput().getText();
        log.debug("Ollama LLM response length={}", text == null ? 0 : text.length());
        return text;
    }
}
