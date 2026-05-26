package com.raccoon.cloud.agent.ai.rag.service;

import com.raccoon.cloud.agent.ai.rag.config.RagVectorStoreConfiguration;
import com.raccoon.cloud.agent.ai.rag.dto.MilvusChunkVO;
import com.raccoon.cloud.agent.ai.rag.dto.MilvusCollectionStatsVO;
import com.raccoon.cloud.agent.ai.rag.dto.Neo4jNodeVO;
import com.raccoon.cloud.agent.ai.rag.dto.Neo4jOverviewVO;
import com.raccoon.cloud.agent.ai.rag.dto.Neo4jRelationshipVO;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.index.DescribeIndexParam;
import io.milvus.response.DescCollResponseWrapper;
import io.milvus.response.GetCollStatResponseWrapper;
import io.milvus.response.QueryResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RAG 底层存储查看服务：Neo4j 节点 / 关系，Milvus 集合 / chunk。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagStorageService {

    /** 允许浏览的节点 label / 关系 type 白名单（防注入） */
    private static final Set<String> ALLOWED_LABELS =
            Set.of("Document", "Device", "Fault");
    private static final Set<String> ALLOWED_REL_TYPES =
            Set.of("HAS_DOCUMENT", "HAS_FAULT", "CONNECTS_TO", "REFERENCES");

    private final Neo4jClient neo4jClient;
    private final MilvusServiceClient milvusServiceClient;
    private final RagVectorStoreConfiguration ragVectorStore;

    // ============== Neo4j ==============

    public Neo4jOverviewVO neo4jOverview() {
        long nodeTotal = scalarLong("MATCH (n) RETURN count(n) AS c", "c");
        long relTotal = scalarLong("MATCH ()-[r]->() RETURN count(r) AS c", "c");

        var labelRows = neo4jClient.query("""
                MATCH (n)
                UNWIND labels(n) AS label
                RETURN label, count(*) AS c
                ORDER BY c DESC
                """).fetch().all();
        List<Neo4jOverviewVO.LabelStat> labels = new ArrayList<>();
        for (Map<String, Object> r : labelRows) {
            labels.add(Neo4jOverviewVO.LabelStat.builder()
                    .label(asString(r.get("label")))
                    .count(asLong(r.get("c")))
                    .build());
        }

        var typeRows = neo4jClient.query("""
                MATCH ()-[r]->()
                RETURN type(r) AS type, count(r) AS c
                ORDER BY c DESC
                """).fetch().all();
        List<Neo4jOverviewVO.RelTypeStat> relTypes = new ArrayList<>();
        for (Map<String, Object> r : typeRows) {
            relTypes.add(Neo4jOverviewVO.RelTypeStat.builder()
                    .type(asString(r.get("type")))
                    .count(asLong(r.get("c")))
                    .build());
        }

        return Neo4jOverviewVO.builder()
                .totalNodes(nodeTotal)
                .totalRelationships(relTotal)
                .labels(labels)
                .relationshipTypes(relTypes)
                .build();
    }

    public List<Neo4jNodeVO> listNodes(String label, int limit) {
        if (!ALLOWED_LABELS.contains(label)) {
            throw new IllegalArgumentException("不支持的节点 label: " + label
                    + "，可选: " + ALLOWED_LABELS);
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String cypher = "MATCH (n:" + label + ") "
                + "RETURN id(n) AS internalId, labels(n) AS labels, properties(n) AS props "
                + "LIMIT $limit";
        var rows = neo4jClient.query(cypher).bind(safeLimit).to("limit").fetch().all();
        List<Neo4jNodeVO> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            result.add(Neo4jNodeVO.builder()
                    .internalId(asLong(r.get("internalId")))
                    .labels(toStringList(r.get("labels")))
                    .properties(asMap(r.get("props")))
                    .build());
        }
        return result;
    }

    public List<Neo4jRelationshipVO> listRelationships(String type, int limit) {
        if (!ALLOWED_REL_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的关系 type: " + type
                    + "，可选: " + ALLOWED_REL_TYPES);
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String cypher = "MATCH (a)-[r:" + type + "]->(b) "
                + "RETURN type(r) AS type, properties(r) AS props, "
                + "labels(a) AS aLabels, properties(a) AS aProps, "
                + "labels(b) AS bLabels, properties(b) AS bProps "
                + "LIMIT $limit";
        var rows = neo4jClient.query(cypher).bind(safeLimit).to("limit").fetch().all();
        List<Neo4jRelationshipVO> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            result.add(Neo4jRelationshipVO.builder()
                    .type(asString(r.get("type")))
                    .properties(asMap(r.get("props")))
                    .startLabel(firstLabel(r.get("aLabels")))
                    .startNode(asMap(r.get("aProps")))
                    .endLabel(firstLabel(r.get("bLabels")))
                    .endNode(asMap(r.get("bProps")))
                    .build());
        }
        return result;
    }

    public Set<String> allowedLabels() {
        return ALLOWED_LABELS;
    }

    public Set<String> allowedRelationshipTypes() {
        return ALLOWED_REL_TYPES;
    }

    // ============== Milvus ==============

    public MilvusCollectionStatsVO milvusStats() {
        String dbName = ragVectorStore.getDatabaseName();
        String collectionName = ragVectorStore.getCollectionName();

        // 1. row count
        long rowCount = 0L;
        try {
            R<GetCollectionStatisticsResponse> stat = milvusServiceClient.getCollectionStatistics(
                    GetCollectionStatisticsParam.newBuilder()
                            .withDatabaseName(dbName)
                            .withCollectionName(collectionName)
                            .withFlush(true)
                            .build());
            if (stat.getStatus() == R.Status.Success.getCode() && stat.getData() != null) {
                rowCount = new GetCollStatResponseWrapper(stat.getData()).getRowCount();
            }
        } catch (Exception e) {
            log.warn("Milvus stats failed: {}", e.getMessage());
        }

        // 2. describe collection (fields)
        List<MilvusCollectionStatsVO.FieldInfo> fields = new ArrayList<>();
        try {
            R<DescribeCollectionResponse> desc = milvusServiceClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withDatabaseName(dbName)
                            .withCollectionName(collectionName)
                            .build());
            if (desc.getStatus() == R.Status.Success.getCode() && desc.getData() != null) {
                DescCollResponseWrapper w = new DescCollResponseWrapper(desc.getData());
                for (var f : w.getFields()) {
                    Integer maxLen = null;
                    Integer dim = null;
                    try {
                        Map<String, String> tp = f.getTypeParams();
                        if (tp != null) {
                            for (var entry : tp.entrySet()) {
                                if ("max_length".equalsIgnoreCase(entry.getKey())) {
                                    maxLen = Integer.parseInt(entry.getValue());
                                } else if ("dim".equalsIgnoreCase(entry.getKey())) {
                                    dim = Integer.parseInt(entry.getValue());
                                }
                            }
                        }
                        if (dim == null && f.getDataType() == DataType.FloatVector) {
                            try {
                                dim = f.getDimension();
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    fields.add(MilvusCollectionStatsVO.FieldInfo.builder()
                            .name(f.getName())
                            .dataType(f.getDataType().name())
                            .primaryKey(f.isPrimaryKey())
                            .maxLength(maxLen)
                            .dimension(f.getDataType() == DataType.FloatVector ? dim : null)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Milvus describeCollection failed: {}", e.getMessage());
        }

        // 3. index info
        String indexType = null;
        String metricType = null;
        try {
            R<DescribeIndexResponse> idx = milvusServiceClient.describeIndex(
                    DescribeIndexParam.newBuilder()
                            .withDatabaseName(dbName)
                            .withCollectionName(collectionName)
                            .build());
            if (idx.getStatus() == R.Status.Success.getCode()
                    && idx.getData() != null
                    && !idx.getData().getIndexDescriptionsList().isEmpty()) {
                var d = idx.getData().getIndexDescriptionsList().get(0);
                for (var kv : d.getParamsList()) {
                    if ("index_type".equalsIgnoreCase(kv.getKey())) indexType = kv.getValue();
                    if ("metric_type".equalsIgnoreCase(kv.getKey())) metricType = kv.getValue();
                }
            }
        } catch (Exception e) {
            log.debug("Milvus describeIndex skipped: {}", e.getMessage());
        }

        return MilvusCollectionStatsVO.builder()
                .databaseName(dbName)
                .collectionName(collectionName)
                .rowCount(rowCount)
                .dimension(ragVectorStore.getDimension())
                .indexType(indexType)
                .metricType(metricType)
                .fields(fields)
                .build();
    }

    /**
     * 浏览 Milvus 中的 chunk 数据。
     * 若提供 docId，则按 metadata['doc_id'] 过滤；否则取所有的最多 limit 条。
     */
    public List<MilvusChunkVO> listChunks(String docId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String expr;
        if (StringUtils.hasText(docId)) {
            expr = "metadata[\"doc_id\"] == \"" + docId.replace("\"", "\\\"") + "\"";
        } else {
            // 全部：用主键非空作为占位条件（Milvus query 必须有 expr）
            expr = "doc_id != \"\"";
        }
        try {
            R<QueryResults> resp = milvusServiceClient.query(QueryParam.newBuilder()
                    .withDatabaseName(ragVectorStore.getDatabaseName())
                    .withCollectionName(ragVectorStore.getCollectionName())
                    .withExpr(expr)
                    .withOutFields(List.of("doc_id", "content", "metadata"))
                    .withLimit((long) safeLimit)
                    .build());
            if (resp.getStatus() != R.Status.Success.getCode() || resp.getData() == null) {
                String msg = resp.getMessage() != null ? resp.getMessage() : ("status=" + resp.getStatus());
                throw new IllegalStateException("Milvus query 失败: " + msg);
            }
            QueryResultsWrapper wrapper = new QueryResultsWrapper(resp.getData());
            List<MilvusChunkVO> result = new ArrayList<>();
            for (QueryResultsWrapper.RowRecord row : wrapper.getRowRecords()) {
                Map<String, Object> fieldMap = row.getFieldValues();
                String docPk = String.valueOf(fieldMap.getOrDefault("doc_id", ""));
                String content = String.valueOf(fieldMap.getOrDefault("content", ""));
                Map<String, Object> metaMap = parseMetadata(fieldMap.get("metadata"));
                result.add(MilvusChunkVO.builder()
                        .docPk(docPk)
                        .content(content)
                        .metadata(metaMap)
                        .docId(asString(metaMap.get("doc_id")))
                        .fileName(asString(metaMap.get("file_name")))
                        .chunkIndex(asInteger(metaMap.get("chunk_index")))
                        .build());
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Milvus listChunks failed: {}", e.getMessage());
            throw new IllegalStateException("Milvus 浏览失败: " + e.getMessage(), e);
        }
    }

    // ============== 内部工具 ==============

    private long scalarLong(String cypher, String field) {
        return neo4jClient.query(cypher)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get(field).asLong())
                .one()
                .orElse(0L);
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    private static Integer asInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : m.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static List<String> toStringList(Object v) {
        List<String> result = new ArrayList<>();
        if (v instanceof Iterable<?> it) {
            for (Object o : it) {
                if (o != null) result.add(o.toString());
            }
        }
        return result;
    }

    private static String firstLabel(Object v) {
        List<String> list = toStringList(v);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Milvus 的 metadata 字段是 JSON 字符串，需要解析；若已是 Map 则直接返回。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(Object raw) {
        if (raw == null) return new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var e : m.entrySet()) {
                if (e.getKey() != null) result.put(e.getKey().toString(), e.getValue());
            }
            return result;
        }
        String s = raw.toString().trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            try {
                return JSON_MAPPER.readValue(s, Map.class);
            } catch (Exception e) {
                log.debug("metadata parse failed: {}", e.getMessage());
            }
        }
        return Map.of("raw", s);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
}
