package com.raccoon.cloud.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaChatResponse {

    private String model;
    private String content;
    private Long elapsedMs;
}
