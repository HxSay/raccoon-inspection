package com.raccoon.cloud.system.cmms.integration;

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
import java.util.Map;

/**
 * 审核通过/驳回后回调 drone 模块执行正式下发或重规划。
 */
@Slf4j
@Component
public class DronePlanningClient {

    @Value("${raccoon.drone.base-url:http://localhost:8091}")
    private String baseUrl;

    @Value("${raccoon.drone.timeout-ms:30000}")
    private long timeoutMs;

    private RestClient restClient;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(3000));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean dispatchApproved(String dispatchTaskId, String planningPayloadJson) {
        try {
            HxResult<?> resp = restClient.post()
                    .uri("/planning/audit/approve-dispatch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "dispatchTaskId", dispatchTaskId != null ? dispatchTaskId : "",
                            "planningPayloadJson", planningPayloadJson != null ? planningPayloadJson : ""))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return resp != null && resp.getCode() == 200;
        } catch (Exception e) {
            log.warn("[DronePlanningClient] 正式下发失败 dispatchTaskId={}: {}", dispatchTaskId, e.getMessage());
            return false;
        }
    }

    public boolean replanRejected(Long workOrderId, String dispatchTaskId, String planningPayloadJson) {
        try {
            HxResult<?> resp = restClient.post()
                    .uri("/planning/audit/replan")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "workOrderId", workOrderId,
                            "dispatchTaskId", dispatchTaskId != null ? dispatchTaskId : "",
                            "planningPayloadJson", planningPayloadJson != null ? planningPayloadJson : ""))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return resp != null && resp.getCode() == 200;
        } catch (Exception e) {
            log.warn("[DronePlanningClient] 驳回重规划失败 workOrderId={}: {}", workOrderId, e.getMessage());
            return false;
        }
    }
}
