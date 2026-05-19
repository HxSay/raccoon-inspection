package com.raccoon.cloud.agent.dto;

import lombok.Data;

@Data
public class Neo4jHealthVO {
    private boolean reachable;
    private String uri;
    private String browserUrl;
    private String version;
    private String edition;
    private String message;
}
