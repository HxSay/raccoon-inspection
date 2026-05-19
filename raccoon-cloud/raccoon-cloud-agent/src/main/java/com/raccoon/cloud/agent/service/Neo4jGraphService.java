package com.raccoon.cloud.agent.service;

import com.raccoon.cloud.agent.dto.Neo4jHealthVO;
import com.raccoon.cloud.agent.dto.Neo4jStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jGraphService {

    private static final Pattern WRITE_KEYWORD = Pattern.compile(
            "\\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP|FOREACH|LOAD\\s+CSV)\\b",
            Pattern.CASE_INSENSITIVE);

    private final Neo4jClient neo4jClient;

    @Value("${spring.neo4j.uri}")
    private String uri;

    @Value("${raccoon.neo4j.browser-url:http://127.0.0.1:7474}")
    private String browserUrl;

    public Neo4jHealthVO health() {
        Neo4jHealthVO vo = new Neo4jHealthVO();
        vo.setUri(uri);
        vo.setBrowserUrl(browserUrl);
        try {
            Long ok = neo4jClient.query("RETURN 1 AS ok")
                    .fetchAs(Long.class)
                    .mappedBy((typeSystem, record) -> record.get("ok").asLong())
                    .one()
                    .orElse(null);
            vo.setReachable(ok != null && ok == 1L);
            if (vo.isReachable()) {
                Map<String, Object> versionRow = neo4jClient.query(
                                """
                                CALL dbms.components()
                                YIELD name, versions, edition
                                WHERE name = 'Neo4j Kernel'
                                RETURN versions[0] AS version, edition AS edition
                                """)
                        .fetch()
                        .one()
                        .orElse(Map.of());
                vo.setVersion(versionRow.get("version") != null ? versionRow.get("version").toString() : null);
                vo.setEdition(versionRow.get("edition") != null ? versionRow.get("edition").toString() : null);
                vo.setMessage("Neo4j 连接正常");
            } else {
                vo.setMessage("Neo4j 探活失败");
            }
        } catch (Exception e) {
            vo.setReachable(false);
            vo.setMessage("无法连接 Neo4j: " + e.getMessage());
            log.warn("neo4j health check failed: {}", e.getMessage());
        }
        return vo;
    }

    public Neo4jStatsVO stats() {
        ensureConnected();
        Neo4jStatsVO stats = new Neo4jStatsVO();
        stats.setNodeCount(scalarLong("MATCH (n) RETURN count(n) AS c", "c"));
        stats.setRelationshipCount(scalarLong("MATCH ()-[r]->() RETURN count(r) AS c", "c"));
        stats.setLabels(listStrings("CALL db.labels() YIELD label RETURN label ORDER BY label", "label"));
        stats.setRelationshipTypes(
                listStrings("CALL db.relationshipTypes() YIELD relationshipType AS type RETURN type ORDER BY type", "type"));
        return stats;
    }

    public List<Map<String, Object>> runReadQuery(String cypher) {
        ensureConnected();
        validateReadOnly(cypher);
        Collection<Map<String, Object>> rows = neo4jClient.query(cypher.trim()).fetch().all();
        return new ArrayList<>(rows);
    }

    private void ensureConnected() {
        Neo4jHealthVO health = health();
        if (!health.isReachable()) {
            throw new IllegalStateException(health.getMessage());
        }
    }

    private void validateReadOnly(String cypher) {
        String trimmed = cypher.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Cypher 不能为空");
        }
        if (WRITE_KEYWORD.matcher(trimmed).find()) {
            throw new IllegalArgumentException("仅允许只读查询（MATCH / RETURN / CALL db.* 等），禁止写入类语句");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("MATCH")
                && !upper.startsWith("RETURN")
                && !upper.startsWith("CALL")
                && !upper.startsWith("WITH")
                && !upper.startsWith("UNWIND")
                && !upper.startsWith("SHOW")
                && !upper.startsWith("OPTIONAL")) {
            throw new IllegalArgumentException("仅支持以 MATCH、RETURN、CALL、WITH、UNWIND、SHOW、OPTIONAL 开头的只读 Cypher");
        }
    }

    private long scalarLong(String cypher, String field) {
        return neo4jClient.query(cypher)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get(field).asLong())
                .one()
                .orElse(0L);
    }

    private List<String> listStrings(String cypher, String field) {
        return neo4jClient.query(cypher)
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get(field).asString())
                .all()
                .stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }
}
