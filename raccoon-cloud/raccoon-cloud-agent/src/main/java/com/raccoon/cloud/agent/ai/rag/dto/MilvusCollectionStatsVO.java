package com.raccoon.cloud.agent.ai.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Milvus 集合统计。
 */
@Data
@Builder
public class MilvusCollectionStatsVO {

    private String databaseName;

    private String collectionName;

    private long rowCount;

    private int dimension;

    private String indexType;

    private String metricType;

    private List<FieldInfo> fields;

    @Data
    @Builder
    public static class FieldInfo {
        private String name;
        private String dataType;
        private boolean primaryKey;
        private Integer maxLength;
        private Integer dimension;
    }
}
