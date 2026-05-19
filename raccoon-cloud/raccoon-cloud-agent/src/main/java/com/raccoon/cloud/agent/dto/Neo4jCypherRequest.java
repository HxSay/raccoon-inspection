package com.raccoon.cloud.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Neo4jCypherRequest {
    @NotBlank(message = "Cypher 不能为空")
    private String cypher;
}
