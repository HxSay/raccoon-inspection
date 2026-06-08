package com.raccoon.cloud.iotdata.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.iotdata.entity.UavInspectionSample;
import com.raccoon.cloud.iotdata.entity.UavInspectionSession;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UavInspectionRagArchiveClient {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    @Value("${raccoon.rag-archive.enabled:true}")
    private boolean enabled;

    @Value("${raccoon.rag-archive.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${raccoon.rag-archive.timeout-ms:10000}")
    private long timeoutMs;

    private RestClient restClient;

    public UavInspectionRagArchiveClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 3000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        log.info("[uav-rag-archive-client] enabled={} baseUrl={} timeoutMs={}", enabled, baseUrl, timeoutMs);
    }

    public ArchiveResult archive(UavInspectionSession session, List<UavInspectionSample> samples) {
        if (!enabled) {
            return ArchiveResult.skipped("RAG archive disabled");
        }
        if (session == null || session.getId() == null) {
            return ArchiveResult.skipped("Missing session id");
        }
        try {
            HxResult<Map<String, Object>> resp = restClient.post()
                    .uri("/api/rag/uav-inspection/archive")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(session, samples == null ? List.of() : samples))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (resp == null) {
                return ArchiveResult.failed("Agent archive response is empty");
            }
            if (resp.getCode() != 200 || resp.getData() == null) {
                return ArchiveResult.failed(resp.getMsg() != null ? resp.getMsg() : "Agent archive failed");
            }
            Map<String, Object> data = resp.getData();
            return ArchiveResult.success(
                    asString(data.get("docId")),
                    asInteger(data.get("chunkCount")),
                    asString(data.get("message"))
            );
        } catch (Exception e) {
            log.warn("[uav-rag-archive-client] archive failed, sessionId={}: {}", session.getId(), e.getMessage(), e);
            return ArchiveResult.failed("Agent archive failed: " + rootCause(e));
        }
    }

    private Map<String, Object> buildBody(UavInspectionSession session, List<UavInspectionSample> samples) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("sessionId", session.getId());
        s.put("uavId", session.getUavId());
        s.put("taskId", session.getTaskId());
        s.put("planId", session.getPlanId());
        s.put("mapId", session.getMapId());
        s.put("startedAt", format(session.getStartedAt()));
        s.put("finishedAt", format(session.getFinishedAt()));
        s.put("distanceM", session.getDistanceM());
        s.put("sampleCount", session.getSampleCount());
        body.put("session", s);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (UavInspectionSample sample : samples) {
            if (sample == null || sample.getId() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sampleId", sample.getId());
            row.put("waypointIndex", sample.getWaypointIndex());
            row.put("modalityType", sample.getModalityType());
            row.put("capturedAt", format(sample.getCapturedAt()));
            row.put("longitude", sample.getLongitude());
            row.put("latitude", sample.getLatitude());
            row.put("height", sample.getHeight());
            row.put("payload", parsePayload(sample.getPayloadJson()));
            rows.add(row);
        }
        body.put("samples", rows);
        return body;
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of("raw", json);
        }
    }

    private static String format(java.time.LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String rootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    public record ArchiveResult(boolean success, String docId, Integer chunkCount, String message) {
        static ArchiveResult success(String docId, Integer chunkCount, String message) {
            return new ArchiveResult(true, docId, chunkCount, message);
        }

        static ArchiveResult failed(String message) {
            return new ArchiveResult(false, null, null, message);
        }

        static ArchiveResult skipped(String message) {
            return new ArchiveResult(false, null, null, message);
        }
    }
}
