package com.raccoon.cloud.drone.planning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskRequest;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskResponse;
import com.raccoon.cloud.drone.dispatch.model.GlobalPathPlan;
import com.raccoon.cloud.drone.dispatch.service.TaskGenerateService;
import com.raccoon.cloud.drone.llm.dto.NlpTaskParseResponse;
import com.raccoon.cloud.drone.llm.model.LlmTaskSlotResult;
import com.raccoon.cloud.drone.llm.service.NlpTaskParseFacadeService;
import com.raccoon.cloud.drone.planning.integration.SystemCmmsClient;
import com.raccoon.common.dto.planning.PlanningEndToEndRequest;
import com.raccoon.common.dto.planning.PlanningEndToEndResponse;
import com.raccoon.common.dto.planning.PlanningWorkOrderSubmitRequest;
import com.raccoon.common.dto.planning.PlanningWorkOrderSubmitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
/**
 * 任务规划 Agent 端到端：NLP 解析 → 多机协同调度（不下发）→ CMMS 工单待审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningEndToEndService {

    private final NlpTaskParseFacadeService nlpTaskParseFacadeService;
    private final TaskGenerateService taskGenerateService;
    private final SystemCmmsClient systemCmmsClient;
    private final ObjectMapper objectMapper;

    public PlanningEndToEndResponse run(PlanningEndToEndRequest req) {
        PlanningEndToEndResponse out = new PlanningEndToEndResponse();
        if (req == null) {
            out.setSuccess(false);
            out.setMessage("请求不能为空");
            return out;
        }

        DispatchTaskRequest dispatchReq = buildDispatchRequest(req);
        if (StringUtils.hasText(req.getUserInput())) {
            NlpTaskParseResponse nlp = nlpTaskParseFacadeService.parse(req.getUserInput().trim());
            if (nlp.isNeedFollowUp()) {
                out.setSuccess(false);
                out.setNeedFollowUp(true);
                out.setFollowUpQuestion(nlp.getFollowUpQuestion());
                out.setMessage("需补充信息");
                return out;
            }
            if (nlp.getSlots() != null) {
                mergeSlots(dispatchReq, nlp.getSlots());
            }
        }

        dispatchReq.setAutoDispatch(false);
        if (req.getEnableSimulation() != null) {
            dispatchReq.setEnableSimulation(req.getEnableSimulation());
        }

        String payloadJson = toJson(dispatchReq);
        DispatchTaskResponse dispatch = taskGenerateService.generate(dispatchReq);
        out.setDispatchTaskId(dispatch.getTaskId());
        out.setAssignedTerminalId(dispatch.getAssignedTerminalId());
        out.setAssignedTerminalName(dispatch.getAssignedTerminalName());
        out.setAssignReason(dispatch.getAssignReason());

        if (!dispatch.isAssigned()) {
            out.setSuccess(false);
            out.setMessage(dispatch.getMessage() != null ? dispatch.getMessage() : "未分配到可用终端");
            return out;
        }

        PlanningWorkOrderSubmitRequest submit = toSubmit(req, dispatch, dispatchReq);
        try {
            PlanningWorkOrderSubmitResponse cmms = systemCmmsClient.submit(submit);
            systemCmmsClient.storePayload(cmms.getWorkOrderId(), payloadJson);
            fillCmmsResult(out, cmms, true, "已生成待审核巡检工单");
        } catch (Exception e) {
            log.error("[PlanningE2E] CMMS 提交失败", e);
            out.setSuccess(false);
            out.setMessage("调度成功但工单提交失败: " + e.getMessage());
        }
        return out;
    }

    public boolean approveDispatch(String dispatchTaskId, String planningPayloadJson) {
        DispatchTaskRequest req = parsePayload(planningPayloadJson);
        req.setAutoDispatch(true);
        DispatchTaskResponse resp = taskGenerateService.generate(req);
        boolean ok = resp.isAssigned() && resp.getWorkOrder() != null;
        log.info("[PlanningE2E] 审核通过正式下发 dispatchTaskId={} ok={}", dispatchTaskId, ok);
        return ok;
    }

    public boolean replanRejected(Long workOrderId, String dispatchTaskId, String planningPayloadJson) {
        DispatchTaskRequest req = parsePayload(planningPayloadJson);
        req.setAutoDispatch(false);
        DispatchTaskResponse dispatch = taskGenerateService.generate(req);
        if (!dispatch.isAssigned()) {
            log.warn("[PlanningE2E] 驳回重规划未分配 workOrderId={}", workOrderId);
            return false;
        }
        PlanningWorkOrderSubmitRequest submit = new PlanningWorkOrderSubmitRequest();
        submit.setDispatchTaskId(dispatch.getTaskId());
        submit.setRequestId(dispatch.getRequestId());
        submit.setMapId(req.getMapId());
        submit.setAreaName(req.getAreaName());
        submit.setTaskTypeCode(req.getTaskType());
        submit.setPriorityCode(req.getPriority());
        submit.setDeviceNames(req.getDeviceNames());
        submit.setUserInput(req.getUserInput());
        submit.setRemark("审核驳回后自动重规划");
        submit.setAssignedTerminalId(dispatch.getAssignedTerminalId());
        submit.setAssignedTerminalName(dispatch.getAssignedTerminalName());
        submit.setAssignReason(dispatch.getAssignReason());
        submit.setPathPlanJson(pathPlanJson(dispatch.getPathPlan()));
        try {
            systemCmmsClient.resubmit(workOrderId, submit);
            systemCmmsClient.storePayload(workOrderId, toJson(req));
            return true;
        } catch (Exception e) {
            log.error("[PlanningE2E] 重提交 CMMS 失败 workOrderId={}", workOrderId, e);
            return false;
        }
    }

    private DispatchTaskRequest buildDispatchRequest(PlanningEndToEndRequest req) {
        DispatchTaskRequest d = new DispatchTaskRequest();
        d.setRequestId(req.getRequestId());
        d.setUserInput(req.getUserInput());
        d.setMapId(req.getMapId());
        d.setAreaName(req.getAreaName());
        d.setTaskType(req.getTaskType());
        d.setPriority(req.getPriority());
        if (req.getDeviceNames() != null) {
            d.setDeviceNames(new ArrayList<>(req.getDeviceNames()));
        }
        d.setRemark(req.getRemark());
        d.setAutoDispatch(false);
        d.setEnableSimulation(req.getEnableSimulation() != null ? req.getEnableSimulation() : Boolean.TRUE);
        return d;
    }

    private void mergeSlots(DispatchTaskRequest d, LlmTaskSlotResult slots) {
        if (StringUtils.hasText(slots.getAreaName())) {
            d.setAreaName(slots.getAreaName());
        }
        if (slots.getDeviceNames() != null && !slots.getDeviceNames().isEmpty()) {
            d.setDeviceNames(new ArrayList<>(slots.getDeviceNames()));
        }
        if (StringUtils.hasText(slots.getTaskType())) {
            d.setTaskType(slots.getTaskType());
        }
        if (StringUtils.hasText(slots.getPriority())) {
            d.setPriority(slots.getPriority());
        }
        if (StringUtils.hasText(slots.getRemark())) {
            d.setRemark(slots.getRemark());
        }
    }

    private PlanningWorkOrderSubmitRequest toSubmit(PlanningEndToEndRequest req,
                                                    DispatchTaskResponse dispatch,
                                                    DispatchTaskRequest dispatchReq) {
        PlanningWorkOrderSubmitRequest s = new PlanningWorkOrderSubmitRequest();
        s.setDispatchTaskId(dispatch.getTaskId());
        s.setRequestId(dispatch.getRequestId());
        s.setMapId(dispatchReq.getMapId());
        s.setAreaName(dispatchReq.getAreaName());
        s.setTaskTypeCode(dispatch.getTaskTypeCode());
        s.setPriorityCode(dispatch.getPriority() != null ? dispatch.getPriority().name() : dispatchReq.getPriority());
        s.setDeviceNames(dispatchReq.getDeviceNames());
        s.setUserInput(dispatchReq.getUserInput());
        s.setRemark(dispatchReq.getRemark());
        s.setInspectorId(req.getInspectorId());
        s.setInspectorName(req.getInspectorName());
        s.setAssignedTerminalId(dispatch.getAssignedTerminalId());
        s.setAssignedTerminalName(dispatch.getAssignedTerminalName());
        s.setAssignReason(dispatch.getAssignReason());
        s.setPathPlanJson(pathPlanJson(dispatch.getPathPlan()));
        return s;
    }

    private String pathPlanJson(GlobalPathPlan plan) {
        if (plan == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private DispatchTaskRequest parsePayload(String json) {
        if (!StringUtils.hasText(json)) {
            return new DispatchTaskRequest();
        }
        try {
            return objectMapper.readValue(json, DispatchTaskRequest.class);
        } catch (Exception e) {
            log.warn("[PlanningE2E] 解析 planningPayload 失败，使用空请求: {}", e.getMessage());
            return new DispatchTaskRequest();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private void fillCmmsResult(PlanningEndToEndResponse out, PlanningWorkOrderSubmitResponse cmms,
                                boolean success, String message) {
        out.setSuccess(success);
        out.setMessage(message);
        out.setPlanId(cmms.getPlanId());
        out.setTaskId(cmms.getTaskId());
        out.setWorkOrderId(cmms.getWorkOrderId());
        out.setOrderNo(cmms.getOrderNo());
        out.setWorkOrderStatus(cmms.getStatus());
        out.setAuditDeadline(cmms.getAuditDeadline());
    }
}
