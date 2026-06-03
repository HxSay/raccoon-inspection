package com.raccoon.cloud.system.cmms.integration;

import com.raccoon.common.dto.planning.WorkOrderAuditApproveResult;
import com.raccoon.common.result.HxResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
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

    public WorkOrderAuditApproveResult dispatchApproved(String dispatchTaskId, String planningPayloadJson,
                                                        Long assignedTerminalId) {
        WorkOrderAuditApproveResult out = new WorkOrderAuditApproveResult();
        if (!StringUtils.hasText(planningPayloadJson)) {
            out.setDispatched(false);
            out.setMessage("缺少规划载荷，无法正式下发（请重新生成待审工单）");
            return out;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("dispatchTaskId", dispatchTaskId != null ? dispatchTaskId : "");
            body.put("planningPayloadJson", planningPayloadJson);
            if (assignedTerminalId != null) {
                body.put("assignedTerminalId", assignedTerminalId);
            }
            HxResult<Map<String, Object>> resp = restClient.post()
                    .uri("/planning/audit/approve-dispatch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
                out.setDispatched(false);
                out.setMessage(resp != null ? resp.getMsg() : "drone 服务无响应");
                return out;
            }
            Map<String, Object> data = resp.getData();
            out.setDispatched(Boolean.TRUE.equals(data.get("dispatched")));
            out.setDispatchPayload(data.get("dispatchPayload"));
            out.setMessage("正式下发成功");
            return out;
        } catch (Exception e) {
            log.warn("[DronePlanningClient] 正式下发失败 dispatchTaskId={}: {}", dispatchTaskId, e.getMessage());
            out.setDispatched(false);
            out.setMessage("正式下发失败: " + e.getMessage());
            return out;
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
