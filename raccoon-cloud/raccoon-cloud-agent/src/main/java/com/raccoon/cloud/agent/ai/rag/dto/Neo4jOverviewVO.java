package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Neo4j 存储总览：节点总数、按 label 分布；关系总数、按 type 分布。
 */
@Data
@Builder
public class Neo4jOverviewVO {

    private long totalNodes;

    private long totalRelationships;

    private List<LabelStat> labels;

    private List<RelTypeStat> relationshipTypes;

    @Data
    @Builder
    public static class LabelStat {
        private String label;
        private long count;
    }

    @Data
    @Builder
    public static class RelTypeStat {
        private String type;
        private long count;
    }
}
