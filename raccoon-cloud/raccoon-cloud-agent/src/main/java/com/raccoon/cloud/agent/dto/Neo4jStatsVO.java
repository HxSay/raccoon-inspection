package com.raccoon.cloud.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Neo4jStatsVO {
    private long nodeCount;
    private long relationshipCount;
    private List<String> labels = new ArrayList<>();
    private List<String> relationshipTypes = new ArrayList<>();
}
