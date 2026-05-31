package com.raccoon.cloud.drone.dispatch.knowledge;

import com.raccoon.cloud.drone.dispatch.model.knowledge.DeviceFaultDocRelation;
import com.raccoon.cloud.drone.dispatch.model.knowledge.DocumentChunk;
import com.raccoon.cloud.drone.dispatch.model.knowledge.TopologyNode;
import com.raccoon.common.result.HxResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 中央 Agent 任务规划 → raccoon-cloud-agent 混合 RAG 知识检索客户端。
 * <p>通过 REST 调用 agent 模块的 Milvus 向量检索（/api/rag/search）与 Neo4j 图查询（/neo4j/cypher/read）。
 * 任何网络/服务异常均降级为空结果，不阻断调度主流程。
 *
 * @author raccoon
 */
@Slf4j
@Component
public class AgentKnowledgeClient {

    @Value("${raccoon.knowledge.enabled:true}")
    private boolean enabled;

    @Value("${raccoon.knowledge.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${raccoon.knowledge.timeout-ms:1500}")
    private long timeoutMs;

    private RestClient restClient;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 1000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        log.info("[knowledge-client] 初始化 enabled={} baseUrl={} timeoutMs={}", enabled, baseUrl, timeoutMs);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Milvus 语义检索巡检规程 / 故障手册片段。
     */
    public List<DocumentChunk> searchRegulations(String query, int topK) {
        if (!enabled || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            HxResult<List<Map<String, Object>>> resp = restClient.post()
                    .uri("/api/rag/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "topK", topK))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (resp == null || resp.getData() == null) {
                return List.of();
            }
            List<DocumentChunk> chunks = new ArrayList<>();
            for (Map<String, Object> row : resp.getData()) {
                DocumentChunk c = new DocumentChunk();
                String docId = str(row.get("docId"));
                Object chunkIndex = row.get("chunkIndex");
                c.setChunkId(chunkIndex != null ? docId + "#" + chunkIndex : docId);
                c.setFileName(str(row.get("fileName")));
                c.setMinioUrl(str(row.get("minioUrl")));
                c.setExcerpt(truncate(str(row.get("content")), 500));
                c.setScore(dbl(row.get("score")));
                chunks.add(c);
            }
            return chunks;
        } catch (Exception e) {
            log.warn("[knowledge-client] 规程检索失败 query={} : {}", query, e.getMessage());
            return List.of();
        }
    }

    /**
     * Neo4j 查询设备-故障-文档关联。
     */
    public List<DeviceFaultDocRelation> queryFaultRelations(List<Long> deviceIds) {
        if (!enabled || deviceIds == null || deviceIds.isEmpty()) {
            return List.of();
        }
        String idList = toQuotedIdList(deviceIds);
        String cypher = "MATCH (d:Device)-[:HAS_FAULT]->(f:Fault) "
                + "WHERE toString(d.deviceId) IN [" + idList + "] "
                + "OPTIONAL MATCH (d)-[:HAS_DOCUMENT]->(doc:Document) "
                + "RETURN d.deviceId AS deviceId, coalesce(d.name, toString(d.deviceId)) AS deviceName, "
                + "f.faultCode AS faultCode, coalesce(f.name, f.faultCode) AS faultName, "
                + "f.level AS severity, doc.fileName AS docName, doc.minioPath AS docUrl "
                + "ORDER BY f.level DESC LIMIT 20";
        return runCypher(cypher).stream().map(row -> {
            DeviceFaultDocRelation r = new DeviceFaultDocRelation();
            r.setDeviceId(lng(row.get("deviceId")));
            r.setDeviceName(str(row.get("deviceName")));
            r.setFaultCode(str(row.get("faultCode")));
            r.setFaultName(str(row.get("faultName")));
            r.setSeverity(severity(row.get("severity")));
            r.setDocName(str(row.get("docName")));
            r.setDocUrl(str(row.get("docUrl")));
            return r;
        }).collect(Collectors.toList());
    }

    /**
     * Neo4j 查询设备上下游拓扑（下游 1~2 层）。
     */
    public List<TopologyNode> queryTopology(List<Long> deviceIds) {
        if (!enabled || deviceIds == null || deviceIds.isEmpty()) {
            return List.of();
        }
        String idList = toQuotedIdList(deviceIds);
        String cypher = "MATCH (d:Device)-[:CONNECTS_TO*1..2]->(down:Device) "
                + "WHERE toString(d.deviceId) IN [" + idList + "] "
                + "RETURN d.deviceId AS sourceId, down.deviceId AS targetId, "
                + "coalesce(down.name, toString(down.deviceId)) AS targetName, "
                + "down.criticalLevel AS criticalLevel LIMIT 50";
        return runCypher(cypher).stream().map(row -> {
            TopologyNode n = new TopologyNode();
            n.setSourceId(lng(row.get("sourceId")));
            n.setTargetId(lng(row.get("targetId")));
            n.setTargetName(str(row.get("targetName")));
            n.setCriticalLevel(dbl(row.get("criticalLevel")));
            return n;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> runCypher(String cypher) {
        try {
            HxResult<List<Map<String, Object>>> resp = restClient.post()
                    .uri("/neo4j/cypher/read")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cypher", cypher))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return resp == null || resp.getData() == null ? List.of() : resp.getData();
        } catch (Exception e) {
            log.warn("[knowledge-client] 图查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String toQuotedIdList(List<Long> ids) {
        return ids.stream().filter(java.util.Objects::nonNull)
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(","));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Long lng(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double dbl(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 严重程度：数值直接用；文本（高/中/低/严重）映射为分值 */
    private Double severity(Object o) {
        Double d = dbl(o);
        if (d != null) {
            return d;
        }
        String s = str(o);
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.contains("严重") || t.contains("高") || t.equalsIgnoreCase("HIGH") || t.equalsIgnoreCase("CRITICAL")) {
            return 5.0;
        }
        if (t.contains("中") || t.equalsIgnoreCase("MEDIUM")) {
            return 3.0;
        }
        if (t.contains("低") || t.equalsIgnoreCase("LOW")) {
            return 1.0;
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
