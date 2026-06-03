package com.raccoon.cloud.drone.planning.web;

import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskResponse;
import com.raccoon.cloud.drone.dispatch.model.DispatchWorkOrder;
import com.raccoon.cloud.drone.planning.service.PlanningEndToEndService;
import com.raccoon.common.dto.planning.PlanningEndToEndRequest;
import com.raccoon.common.dto.planning.PlanningEndToEndResponse;
import com.raccoon.common.result.HxResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务规划 Agent 端到端 REST。
 */
@RestController
@RequiredArgsConstructor
public class PlanningEndToEndController {

    private final PlanningEndToEndService planningEndToEndService;

    @PostMapping("/planning/end-to-end")
    public HxResult<PlanningEndToEndResponse> endToEnd(@RequestBody PlanningEndToEndRequest request) {
        return HxResult.success(planningEndToEndService.run(request));
    }

    @PostMapping("/planning/audit/approve-dispatch")
    public HxResult<Map<String, Object>> approveDispatch(@RequestBody Map<String, Object> body) {
        Long terminalId = null;
        Object tid = body.get("assignedTerminalId");
        if (tid instanceof Number n) {
            terminalId = n.longValue();
        } else if (tid != null) {
            try {
                terminalId = Long.parseLong(String.valueOf(tid));
            } catch (NumberFormatException ignored) {
                terminalId = null;
            }
        }
        DispatchTaskResponse resp = planningEndToEndService.approveDispatch(
                String.valueOf(body.get("dispatchTaskId")),
                String.valueOf(body.get("planningPayloadJson")),
                terminalId);
        DispatchWorkOrder order = resp.getWorkOrder();
        boolean ok = resp.isAssigned()
                && order != null
                && "SUCCESS".equals(order.getDispatchResult());
        if (!ok) {
            return HxResult.fail(resp.getMessage() != null ? resp.getMessage() : "正式下发失败，请检查终端状态或规划载荷");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("dispatched", true);
        data.put("dispatchTaskId", resp.getTaskId());
        data.put("assignedTerminalId", resp.getAssignedTerminalId());
        data.put("assignedTerminalName", resp.getAssignedTerminalName());
        if (order.getPayload() != null) {
            data.put("dispatchPayload", order.getPayload());
        }
        return HxResult.success(data);
    }

    @PostMapping("/planning/audit/replan")
    public HxResult<Map<String, Object>> replan(@RequestBody Map<String, Object> body) {
        Long workOrderId = body.get("workOrderId") instanceof Number n ? n.longValue()
                : Long.parseLong(String.valueOf(body.get("workOrderId")));
        boolean ok = planningEndToEndService.replanRejected(
                workOrderId,
                String.valueOf(body.get("dispatchTaskId")),
                String.valueOf(body.get("planningPayloadJson")));
        return ok ? HxResult.success(Map.of("replanned", true))
                : HxResult.fail("驳回重规划失败");
    }
}
