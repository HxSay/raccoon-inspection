package com.raccoon.cloud.iotdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.iotdata.entity.UavInspectionSample;
import com.raccoon.cloud.iotdata.entity.UavInspectionSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将一次模拟巡检完成后的多模态结果归档到 Neo4j：
 * InspectionSession -> InspectionWaypoint -> MultimodalSample，并关联 UAV / Task / Plan / Map / DataModality。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "raccoon.neo4j", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UavInspectionGraphArchiveService {

    private final Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper;

    public GraphArchiveResult archive(UavInspectionSession session, List<UavInspectionSample> samples) {
        if (session == null || session.getId() == null) {
            return GraphArchiveResult.skipped("sessionId 为空，跳过 Neo4j 归档");
        }
        try {
            upsertSession(session);
            upsertSamples(session, samples == null ? List.of() : samples);
            return GraphArchiveResult.success("Neo4j 已归档巡检会话与多模态采样");
        } catch (Exception e) {
            log.warn("[iot-neo4j] archive multimodal inspection failed, sessionId={}: {}",
                    session.getId(), e.getMessage(), e);
            return GraphArchiveResult.failed("Neo4j 归档失败: " + e.getMessage());
        }
    }

    private void upsertSession(UavInspectionSession session) {
        Map<String, Object> props = new LinkedHashMap<>();
        putIfNotNull(props, "uavId", session.getUavId());
        putIfNotNull(props, "taskId", session.getTaskId());
        putIfNotNull(props, "planId", session.getPlanId());
        putIfNotNull(props, "mapId", session.getMapId());
        putIfNotNull(props, "startedAt", session.getStartedAt());
        putIfNotNull(props, "finishedAt", session.getFinishedAt());
        putIfNotNull(props, "distanceM", session.getDistanceM());
        putIfNotNull(props, "sampleCount", session.getSampleCount());
        putIfNotNull(props, "createTime", session.getCreateTime());
        props.put("source", "raccoon-cloud-iot-data");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sessionId", session.getId());
        params.put("props", props);
        neo4jClient.query("""
                MERGE (s:InspectionSession {sessionId: $sessionId})
                SET s += $props
                """)
                .bindAll(params)
                .run();

        if (session.getUavId() != null) {
            neo4jClient.query("""
                    MATCH (s:InspectionSession {sessionId: $sessionId})
                    MERGE (u:UAV {uavId: $uavId})
                    MERGE (u)-[:EXECUTED_SESSION]->(s)
                    """)
                    .bind(session.getId()).to("sessionId")
                    .bind(session.getUavId()).to("uavId")
                    .run();
        }
        if (session.getTaskId() != null) {
            neo4jClient.query("""
                    MATCH (s:InspectionSession {sessionId: $sessionId})
                    MERGE (t:InspectionTask {taskId: $taskId})
                    MERGE (t)-[:HAS_EXECUTION_SESSION]->(s)
                    """)
                    .bind(session.getId()).to("sessionId")
                    .bind(session.getTaskId()).to("taskId")
                    .run();
        }
        if (session.getPlanId() != null) {
            neo4jClient.query("""
                    MATCH (s:InspectionSession {sessionId: $sessionId})
                    MERGE (p:RoutePlan {planId: $planId})
                    MERGE (p)-[:PRODUCED_SESSION]->(s)
                    """)
                    .bind(session.getId()).to("sessionId")
                    .bind(session.getPlanId()).to("planId")
                    .run();
        }
        if (session.getMapId() != null) {
            neo4jClient.query("""
                    MATCH (s:InspectionSession {sessionId: $sessionId})
                    MERGE (m:InspectionMap {mapId: $mapId})
                    MERGE (s)-[:ON_MAP]->(m)
                    """)
                    .bind(session.getId()).to("sessionId")
                    .bind(session.getMapId()).to("mapId")
                    .run();
        }
    }

    private void upsertSamples(UavInspectionSession session, List<UavInspectionSample> samples) {
        if (samples.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> anomalyRows = new ArrayList<>();
        for (UavInspectionSample sample : samples) {
            if (sample == null || sample.getId() == null) {
                continue;
            }
            Map<String, Object> props = new LinkedHashMap<>();
            putIfNotNull(props, "sampleId", sample.getId());
            putIfNotNull(props, "sessionId", session.getId());
            putIfNotNull(props, "waypointIndex", sample.getWaypointIndex());
            putIfNotNull(props, "modalityType", sample.getModalityType());
            putIfNotNull(props, "capturedAt", sample.getCapturedAt());
            putIfNotNull(props, "longitude", decimalToDouble(sample.getLongitude()));
            putIfNotNull(props, "latitude", decimalToDouble(sample.getLatitude()));
            putIfNotNull(props, "height", sample.getHeight());
            putIfNotNull(props, "payloadJson", sample.getPayloadJson());
            props.put("source", "raccoon-cloud-iot-data");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sampleId", sample.getId());
            row.put("waypointIndex", sample.getWaypointIndex());
            row.put("modalityType", sample.getModalityType());
            row.put("props", props);
            row.put("waypointProps", Map.of(
                    "sessionId", session.getId(),
                    "waypointIndex", sample.getWaypointIndex()
            ));
            rows.add(row);

            Map<String, Object> anomaly = anomalyRow(session, sample);
            if (!anomaly.isEmpty()) {
                anomalyRows.add(anomaly);
            }
        }
        if (rows.isEmpty()) {
            return;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sessionId", session.getId());
        params.put("samples", rows);
        neo4jClient.query("""
                UNWIND $samples AS row
                MATCH (s:InspectionSession {sessionId: $sessionId})
                MERGE (wp:InspectionWaypoint {sessionId: $sessionId, waypointIndex: row.waypointIndex})
                SET wp += row.waypointProps
                MERGE (sample:MultimodalSample {sampleId: row.sampleId})
                SET sample += row.props
                MERGE (modality:DataModality {code: row.modalityType})
                MERGE (s)-[:HAS_WAYPOINT]->(wp)
                MERGE (s)-[:HAS_SAMPLE]->(sample)
                MERGE (wp)-[:HAS_SAMPLE]->(sample)
                MERGE (sample)-[:OF_MODALITY]->(modality)
                """)
                .bindAll(params)
                .run();

        if (!anomalyRows.isEmpty()) {
            Map<String, Object> anomalyParams = new LinkedHashMap<>();
            anomalyParams.put("sessionId", session.getId());
            anomalyParams.put("anomalies", anomalyRows);
            neo4jClient.query("""
                    UNWIND $anomalies AS row
                    MATCH (s:InspectionSession {sessionId: $sessionId})
                    MATCH (sample:MultimodalSample {sampleId: row.sampleId})
                    MERGE (a:InspectionAnomaly {anomalyId: row.anomalyId})
                    SET a += row.props
                    MERGE (s)-[:HAS_ANOMALY]->(a)
                    MERGE (sample)-[:INDICATES_ANOMALY]->(a)
                    """)
                    .bindAll(anomalyParams)
                    .run();
        }
    }

    private Map<String, Object> anomalyRow(UavInspectionSession session, UavInspectionSample sample) {
        Map<String, Object> payload = parsePayload(sample.getPayloadJson());
        boolean anomaly = Boolean.TRUE.equals(payload.get("anomaly"));
        Number anomalyScore = numberValue(payload.get("anomalyScore"));
        Number maxC = numberValue(payload.get("maxC"));
        if (!anomaly && (anomalyScore == null || anomalyScore.doubleValue() < 70.0)
                && (maxC == null || maxC.doubleValue() < 100.0)) {
            return Map.of();
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("anomalyId", "MM-" + sample.getId());
        props.put("sessionId", session.getId());
        props.put("sampleId", sample.getId());
        putIfNotNull(props, "taskId", session.getTaskId());
        putIfNotNull(props, "waypointIndex", sample.getWaypointIndex());
        putIfNotNull(props, "modalityType", sample.getModalityType());
        putIfNotNull(props, "capturedAt", sample.getCapturedAt());
        putIfNotNull(props, "anomalyScore", anomalyScore == null ? null : anomalyScore.doubleValue());
        putIfNotNull(props, "maxC", maxC == null ? null : maxC.doubleValue());
        props.put("source", "multimodal-sim");
        props.put("description", buildAnomalyDescription(sample, anomalyScore, maxC));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sampleId", sample.getId());
        row.put("anomalyId", props.get("anomalyId"));
        row.put("props", props);
        return row;
    }

    private String buildAnomalyDescription(UavInspectionSample sample, Number anomalyScore, Number maxC) {
        if ("THERMAL".equalsIgnoreCase(sample.getModalityType()) && maxC != null) {
            return "热成像温度异常，最高温 " + maxC.doubleValue() + " C";
        }
        if (anomalyScore != null) {
            return sample.getModalityType() + " 模态异常分 " + anomalyScore.doubleValue();
        }
        return sample.getModalityType() + " 模态检测到异常";
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static Double decimalToDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record GraphArchiveResult(boolean success, String message) {
        static GraphArchiveResult success(String message) {
            return new GraphArchiveResult(true, message);
        }

        static GraphArchiveResult failed(String message) {
            return new GraphArchiveResult(false, message);
        }

        static GraphArchiveResult skipped(String message) {
            return new GraphArchiveResult(false, message);
        }
    }
}
