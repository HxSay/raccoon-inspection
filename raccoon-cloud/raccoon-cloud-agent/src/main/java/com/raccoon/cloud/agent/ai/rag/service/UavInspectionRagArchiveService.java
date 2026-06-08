package com.raccoon.cloud.agent.ai.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.agent.ai.rag.config.RagVectorStoreConfiguration;
import com.raccoon.cloud.agent.ai.rag.dto.UavInspectionArchiveRequest;
import com.raccoon.cloud.agent.ai.rag.dto.UavInspectionArchiveResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UavInspectionRagArchiveService {

    private static final String DOC_TYPE = "UAV_INSPECTION_RESULT";
    private static final String META_DOC_ID = "doc_id";
    private static final String META_FILE_NAME = "file_name";
    private static final String META_CHUNK_INDEX = "chunk_index";
    private static final String META_DOC_TYPE = "doc_type";
    private static final String META_SESSION_ID = "session_id";
    private static final String META_SAMPLE_ID = "sample_id";
    private static final String META_WAYPOINT_INDEX = "waypoint_index";
    private static final String META_MODALITY_TYPE = "modality_type";
    private static final String META_DEVICE_ID = "device_id";

    private final RagVectorStoreConfiguration ragVectorStore;
    private final Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper;

    public UavInspectionArchiveResultVO archive(UavInspectionArchiveRequest request) {
        UavInspectionArchiveRequest.Session session = request.getSession();
        String docId = "uav-session-" + session.getSessionId();
        String fileName = "uav-inspection-session-" + session.getSessionId() + ".json";
        List<UavInspectionArchiveRequest.Sample> samples = request.getSamples() == null
                ? List.of()
                : request.getSamples();
        List<Document> documents = buildDocuments(docId, fileName, session, samples);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("No inspection samples to archive");
        }

        replaceVectors(docId, documents);
        upsertGraph(docId, fileName, session, samples, documents.size());

        return UavInspectionArchiveResultVO.builder()
                .docId(docId)
                .fileName(fileName)
                .chunkCount(documents.size())
                .milvusSynced(true)
                .neo4jSynced(true)
                .message("UAV inspection result archived to Neo4j and Milvus")
                .build();
    }

    private List<Document> buildDocuments(String docId,
                                          String fileName,
                                          UavInspectionArchiveRequest.Session session,
                                          List<UavInspectionArchiveRequest.Sample> samples) {
        List<Document> documents = new ArrayList<>(samples.size());
        int chunkIndex = 0;
        for (UavInspectionArchiveRequest.Sample sample : samples) {
            if (sample == null || sample.getSampleId() == null) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(META_DOC_ID, docId);
            metadata.put(META_FILE_NAME, fileName);
            metadata.put(META_CHUNK_INDEX, chunkIndex);
            metadata.put(META_DOC_TYPE, DOC_TYPE);
            metadata.put(META_SESSION_ID, session.getSessionId());
            metadata.put(META_SAMPLE_ID, sample.getSampleId());
            metadata.put(META_WAYPOINT_INDEX, sample.getWaypointIndex());
            metadata.put(META_MODALITY_TYPE, sample.getModalityType());
            String deviceId = firstDeviceId(sample.getPayload());
            if (StringUtils.hasText(deviceId)) {
                metadata.put(META_DEVICE_ID, deviceId);
            }

            String chunkId = chunkId(session.getSessionId(), sample.getSampleId());
            documents.add(Document.builder()
                    .id(chunkId)
                    .text(buildContent(session, sample))
                    .metadata(metadata)
                    .build());
            chunkIndex++;
        }
        return documents;
    }

    private void replaceVectors(String docId, List<Document> documents) {
        try {
            Filter.Expression filter = new FilterExpressionBuilder().eq(META_DOC_ID, docId).build();
            ragVectorStore.getVectorStore().delete(filter);
        } catch (Exception e) {
            log.debug("Skip deleting previous UAV inspection vectors, docId={}, reason={}", docId, e.getMessage());
        }
        ragVectorStore.getVectorStore().add(documents);
    }

    private void upsertGraph(String docId,
                             String fileName,
                             UavInspectionArchiveRequest.Session session,
                             List<UavInspectionArchiveRequest.Sample> samples,
                             int chunkCount) {
        long uploadTime = System.currentTimeMillis();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("docId", docId);
        params.put("fileName", fileName);
        params.put("docType", DOC_TYPE);
        params.put("chunkCount", chunkCount);
        params.put("uploadTime", uploadTime);
        params.put("sessionId", session.getSessionId());
        params.put("uavId", session.getUavId());
        params.put("taskId", session.getTaskId());
        params.put("planId", session.getPlanId());
        params.put("mapId", session.getMapId());
        params.put("startedAt", session.getStartedAt());
        params.put("finishedAt", session.getFinishedAt());
        params.put("distanceM", session.getDistanceM());
        params.put("sampleCount", session.getSampleCount());

        neo4jClient.query("""
                MERGE (d:Document {docId: $docId})
                SET d.fileName = $fileName,
                    d.type = $docType,
                    d.minioPath = '',
                    d.chunkCount = $chunkCount,
                    d.uploadTime = $uploadTime,
                    d.source = 'uav-inspection'
                MERGE (s:InspectionSession {sessionId: $sessionId})
                SET s.uavId = $uavId,
                    s.taskId = $taskId,
                    s.planId = $planId,
                    s.mapId = $mapId,
                    s.startedAt = $startedAt,
                    s.finishedAt = $finishedAt,
                    s.distanceM = $distanceM,
                    s.sampleCount = $sampleCount,
                    s.source = 'uav-inspection'
                MERGE (s)-[:HAS_DOCUMENT]->(d)
                """)
                .bindAll(params)
                .run();

        linkSessionDimensions(session);
        upsertSampleGraph(session, samples);
        attachPayloadDevices(docId, samples);
    }

    private void linkSessionDimensions(UavInspectionArchiveRequest.Session session) {
        if (session.getUavId() != null) {
            neo4jClient.query("""
                    MATCH (s:InspectionSession {sessionId: $sessionId})
                    MERGE (u:UAV {uavId: $uavId})
                    MERGE (u)-[:EXECUTED_SESSION]->(s)
                    """)
                    .bind(session.getSessionId()).to("sessionId")
                    .bind(session.getUavId()).to("uavId")
                    .run();
        }
        if (session.getTaskId() != null) {
            neo4jClient.query("""
                    MATCH (s:InspectionSession {sessionId: $sessionId})
                    MERGE (t:InspectionTask {taskId: $taskId})
                    MERGE (t)-[:HAS_EXECUTION_SESSION]->(s)
                    """)
                    .bind(session.getSessionId()).to("sessionId")
                    .bind(session.getTaskId()).to("taskId")
                    .run();
        }
        if (session.getMapId() != null) {
            neo4jClient.query("""
                    MATCH (s:InspectionSession {sessionId: $sessionId})
                    MERGE (m:InspectionMap {mapId: $mapId})
                    MERGE (s)-[:ON_MAP]->(m)
                    """)
                    .bind(session.getSessionId()).to("sessionId")
                    .bind(session.getMapId()).to("mapId")
                    .run();
        }
    }

    private void upsertSampleGraph(UavInspectionArchiveRequest.Session session,
                                   List<UavInspectionArchiveRequest.Sample> samples) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UavInspectionArchiveRequest.Sample sample : samples) {
            if (sample == null || sample.getSampleId() == null) {
                continue;
            }
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("sampleId", sample.getSampleId());
            props.put("sessionId", session.getSessionId());
            props.put("waypointIndex", sample.getWaypointIndex());
            props.put("modalityType", sample.getModalityType());
            props.put("capturedAt", sample.getCapturedAt());
            props.put("longitude", decimalToDouble(sample.getLongitude()));
            props.put("latitude", decimalToDouble(sample.getLatitude()));
            props.put("height", sample.getHeight());
            props.put("payloadJson", toJson(sample.getPayload()));
            props.put("source", "uav-inspection");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sampleId", sample.getSampleId());
            row.put("waypointIndex", sample.getWaypointIndex());
            row.put("modalityType", sample.getModalityType());
            row.put("props", props);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return;
        }
        neo4jClient.query("""
                UNWIND $samples AS row
                MATCH (s:InspectionSession {sessionId: $sessionId})
                MERGE (wp:InspectionWaypoint {sessionId: $sessionId, waypointIndex: row.waypointIndex})
                SET wp.sessionId = $sessionId,
                    wp.waypointIndex = row.waypointIndex
                MERGE (sample:MultimodalSample {sampleId: row.sampleId})
                SET sample += row.props
                MERGE (modality:DataModality {code: row.modalityType})
                MERGE (s)-[:HAS_WAYPOINT]->(wp)
                MERGE (s)-[:HAS_SAMPLE]->(sample)
                MERGE (wp)-[:HAS_SAMPLE]->(sample)
                MERGE (sample)-[:OF_MODALITY]->(modality)
                """)
                .bind(session.getSessionId()).to("sessionId")
                .bind(rows).to("samples")
                .run();
    }

    private void attachPayloadDevices(String docId, List<UavInspectionArchiveRequest.Sample> samples) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UavInspectionArchiveRequest.Sample sample : samples) {
            String deviceId = firstDeviceId(sample == null ? null : sample.getPayload());
            if (!StringUtils.hasText(deviceId)) {
                continue;
            }
            rows.add(Map.of("deviceId", deviceId));
        }
        if (rows.isEmpty()) {
            return;
        }
        neo4jClient.query("""
                MATCH (d:Document {docId: $docId})
                UNWIND $devices AS dv
                MERGE (dev:Device {deviceId: dv.deviceId})
                  ON CREATE SET dev.name = dv.deviceId, dev.status = 'RUNNING'
                MERGE (dev)-[:HAS_DOCUMENT]->(d)
                """)
                .bind(docId).to("docId")
                .bind(rows).to("devices")
                .run();
    }

    private String buildContent(UavInspectionArchiveRequest.Session session,
                                UavInspectionArchiveRequest.Sample sample) {
        return "UAV inspection sample. "
                + "sessionId=" + session.getSessionId()
                + ", taskId=" + value(session.getTaskId())
                + ", uavId=" + value(session.getUavId())
                + ", waypointIndex=" + value(sample.getWaypointIndex())
                + ", modality=" + value(sample.getModalityType())
                + ", capturedAt=" + value(sample.getCapturedAt())
                + ", location=(" + value(sample.getLatitude()) + "," + value(sample.getLongitude()) + "," + value(sample.getHeight()) + ")"
                + ", payload=" + toJson(sample.getPayload());
    }

    private String firstDeviceId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object direct = payload.get("deviceId");
        if (direct == null) {
            direct = payload.get("device_id");
        }
        if (direct != null && StringUtils.hasText(direct.toString())) {
            return direct.toString().trim();
        }
        Object ids = payload.get("deviceIds");
        if (ids instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    return item.toString().trim();
                }
            }
        }
        return null;
    }

    private String chunkId(Long sessionId, Long sampleId) {
        String raw = "u" + sessionId + "s" + sampleId;
        return raw.length() <= 36 ? raw : raw.substring(0, 36);
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Double decimalToDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
