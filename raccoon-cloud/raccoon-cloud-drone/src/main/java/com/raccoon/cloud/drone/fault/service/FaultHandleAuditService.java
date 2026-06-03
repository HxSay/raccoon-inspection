package com.raccoon.cloud.drone.fault.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.fault.dto.ExpandedScope;
import com.raccoon.cloud.drone.fault.dto.FaultAuditVO;
import com.raccoon.cloud.drone.fault.dto.FaultGradingContext;
import com.raccoon.cloud.drone.fault.dto.FaultHandleResult;
import com.raccoon.cloud.drone.fault.dto.FaultResponsePlan;
import com.raccoon.cloud.drone.fault.dto.ReinspectDispatchResult;
import com.raccoon.cloud.drone.fault.entity.FaultHandleAuditEntity;
import com.raccoon.cloud.drone.fault.enums.FaultHandleStatus;
import com.raccoon.cloud.drone.fault.mapper.FaultHandleAuditMapper;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaultHandleAuditService {

    private final FaultHandleAuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    public void record(FaultEvent event, FaultResponsePlan plan, ExpandedScope scope,
                       FaultGradingContext ctx, ReinspectDispatchResult dispatch, FaultHandleStatus status) {
        try {
            FaultHandleAuditEntity audit = new FaultHandleAuditEntity();
            audit.setEventId(event.getEventId());
            audit.setFaultLevel(event.getLevel() != null ? event.getLevel().getCode() : null);
            audit.setResponseAction(plan != null && plan.getAction() != null ? plan.getAction().getCode() : null);
            audit.setExpandScopeJson(scope != null ? objectMapper.writeValueAsString(scope) : null);
            if (dispatch != null && dispatch.getAssignedTerminalId() != null) {
                audit.setAssignedTerminalIds(String.valueOf(dispatch.getAssignedTerminalId()));
            }
            audit.setReinspectTaskId(dispatch != null ? dispatch.getReinspectTaskId() : null);
            if (ctx != null && ctx.getRegulationChunks() != null) {
                audit.setCitedRegulationIds(objectMapper.writeValueAsString(
                        ctx.getRegulationChunks().stream().map(c -> c.getChunkId()).toList()));
            }
            if (ctx != null && ctx.getGraphSummary() != null) {
                audit.setGraphImpactSummary(objectMapper.writeValueAsString(ctx.getGraphSummary()));
            }
            audit.setHandleTime(LocalDateTime.now());
            audit.setHandleResult(status.getCode());
            FaultHandleResult snapshot = new FaultHandleResult();
            snapshot.setEventId(event.getEventId());
            snapshot.setLevel(event.getLevel());
            snapshot.setPlan(plan);
            snapshot.setExpandedScope(scope);
            snapshot.setDispatch(dispatch);
            audit.setDetailJson(objectMapper.writeValueAsString(snapshot));
            auditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("[fault-audit] 写入失败 eventId={}: {}", event.getEventId(), e.getMessage());
        }
    }

    public List<FaultAuditVO> listRecent(int limit) {
        int n = Math.min(Math.max(limit, 1), 100);
        List<FaultHandleAuditEntity> rows = auditMapper.selectList(
                new LambdaQueryWrapper<FaultHandleAuditEntity>()
                        .orderByDesc(FaultHandleAuditEntity::getHandleTime)
                        .last("LIMIT " + n));
        return rows.stream().map(e -> {
            FaultAuditVO vo = new FaultAuditVO();
            vo.setId(e.getId());
            vo.setEventId(e.getEventId());
            vo.setFaultLevel(e.getFaultLevel());
            vo.setResponseAction(e.getResponseAction());
            vo.setReinspectTaskId(e.getReinspectTaskId());
            vo.setHandleResult(e.getHandleResult());
            vo.setHandleTime(e.getHandleTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
