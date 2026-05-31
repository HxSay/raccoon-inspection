package com.raccoon.cloud.drone.planning.integration;

import com.raccoon.common.dto.planning.PlanningWorkOrderSubmitRequest;
import com.raccoon.common.dto.planning.PlanningWorkOrderSubmitResponse;
import com.raccoon.common.result.HxResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * drone → system CMMS：提交待审核工单、重提交、保存规划载荷。
 */
@Slf4j
@Component
public class SystemCmmsClient {

    @Value("${raccoon.system.base-url:http://localhost:8087}")
    private String baseUrl;

    @Value("${raccoon.system.timeout-ms:15000}")
    private long timeoutMs;

    private RestClient restClient;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(3000));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        log.info("[SystemCmmsClient] baseUrl={}", baseUrl);
    }

    public PlanningWorkOrderSubmitResponse submit(PlanningWorkOrderSubmitRequest req) {
        HxResult<PlanningWorkOrderSubmitResponse> resp = restClient.post()
                .uri("/cmms/agent/planning/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            String msg = resp != null ? resp.getMsg() : "CMMS 无响应";
            throw new IllegalStateException("提交巡检工单失败: " + msg);
        }
        return resp.getData();
    }

    public PlanningWorkOrderSubmitResponse resubmit(Long workOrderId, PlanningWorkOrderSubmitRequest req) {
        String uri = UriComponentsBuilder.fromPath("/cmms/agent/planning/resubmit")
                .queryParam("workOrderId", workOrderId)
                .toUriString();
        HxResult<PlanningWorkOrderSubmitResponse> resp = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            String msg = resp != null ? resp.getMsg() : "CMMS 无响应";
            throw new IllegalStateException("重提交巡检工单失败: " + msg);
        }
        return resp.getData();
    }

    public void storePayload(Long workOrderId, String payloadJson) {
        String uri = UriComponentsBuilder.fromPath("/cmms/agent/planning/store-payload")
                .queryParam("workOrderId", workOrderId)
                .toUriString();
        restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloadJson != null ? payloadJson : "{}")
                .retrieve()
                .toBodilessEntity();
    }
}
