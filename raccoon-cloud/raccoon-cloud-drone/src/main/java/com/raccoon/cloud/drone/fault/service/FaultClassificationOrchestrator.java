package com.raccoon.cloud.drone.fault.service;

import com.raccoon.cloud.drone.fault.dto.ExpandedScope;
import com.raccoon.cloud.drone.fault.dto.FaultGradingContext;
import com.raccoon.cloud.drone.fault.dto.FaultHandleResult;
import com.raccoon.cloud.drone.fault.dto.FaultResponsePlan;
import com.raccoon.cloud.drone.fault.dto.ReinspectDispatchResult;
import com.raccoon.cloud.drone.fault.enums.FaultHandleStatus;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 故障分级处理流水线：定级 → 处置计划 → 扩范围 → 复巡插单 → 审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaultClassificationOrchestrator {

    private final FaultGradingEngine gradingEngine;
    private final FaultGradingKnowledgeService knowledgeService;
    private final FaultResponsePlanService planService;
    private final InspectionScopeExpansionService scopeExpansionService;
    private final ReinspectTaskDispatchService reinspectTaskDispatchService;
    private final FaultHandleAuditService auditService;
    private final FaultEventPersistenceService persistenceService;

    @Transactional
    public FaultHandleResult handle(FaultEvent event) {
        FaultGradingContext ctx = knowledgeService.buildContext(event);
        FaultLevel level = gradingEngine.grade(event);
        event.setLevel(level);
        persistenceService.updateLevel(event);

        FaultResponsePlan plan = planService.buildPlan(event, level);
        ExpandedScope scope = scopeExpansionService.calculate(event, plan);
        ReinspectDispatchResult dispatch = reinspectTaskDispatchService.dispatch(event, plan, scope);

        FaultHandleStatus status = dispatch.isSuccess() ? FaultHandleStatus.SUCCESS : FaultHandleStatus.PARTIAL;
        auditService.record(event, plan, scope, ctx, dispatch, status);

        FaultHandleResult result = new FaultHandleResult();
        result.setEventId(event.getEventId());
        result.setStatus(status);
        result.setLevel(level);
        result.setPlan(plan);
        result.setExpandedScope(scope);
        result.setDispatch(dispatch);
        int devN = scope.getAllDeviceIds().size();
        result.setMessage(dispatch.isSuccess()
                ? "故障分级处置完成，已扩范围纳入 " + devN + " 个设备并插单应急复巡"
                : "定级完成，复巡插单部分失败：" + dispatch.getMessage());
        log.info("[fault-orchestrator] eventId={} level={} dispatchOk={}",
                event.getEventId(), level.getCode(), dispatch.isSuccess());
        return result;
    }
}
