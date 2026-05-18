package com.raccoon.cloud.agent.dto;

import lombok.Data;

@Data
public class OllamaHealthVO {

    private boolean reachable;
    private String baseUrl;
    private String defaultModel;
    private String version;
    private String message;
}
