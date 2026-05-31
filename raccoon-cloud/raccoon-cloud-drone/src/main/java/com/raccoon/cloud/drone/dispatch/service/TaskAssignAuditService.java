package com.raccoon.cloud.drone.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.dispatch.model.BidPrice;
import com.raccoon.cloud.drone.dispatch.model.DispatchInspectionTask;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.entity.TaskAssignAudit;
import com.raccoon.cloud.drone.mapper.TaskAssignAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务分配审计落库服务（步骤 6）。
 * <p>记录每次分配的决策、知识引用、竞拍明细，失败不影响主流程。
 *
 * @author raccoon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssignAuditService {

    private final TaskAssignAuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    /**
     * 异步无关键路径地落库分配审计。任何异常仅告警，不抛出。
     */
    public void record(DispatchInspectionTask task, TerminalState terminal, List<BidPrice> bidMatrix) {
        try {
            TaskAssignAudit audit = new TaskAssignAudit();
            audit.setTaskId(task.getTaskId());
            audit.setRequestId(task.getRequestId());
            audit.setMapId(task.getMapId());
            audit.setTerminalId(task.getAssignedTerminalId());
            audit.setTerminalName(terminal != null ? terminal.getTerminalName() : null);
            audit.setAssigned(task.getAssignedTerminalId() != null);
            audit.setPriority(task.getPriority() != null ? task.getPriority().getCode() : null);
            audit.setPriorityScore(task.getPriorityScore() != null
                    ? task.getPriorityScore().getFinalScore() : null);
            audit.setBidPrice(task.getBidFinalPrice());
            audit.setAssignReason(truncate(task.getAssignReason(), 1000));
            audit.setRequiredSensors(task.getRequiredSensors() == null ? null
                    : String.join(",", task.getRequiredSensors()));
            audit.setCitedChunkIds(toJson(task.getCitedChunkIds()));
            audit.setCitedGraphRefs(toJson(task.getCitedGraphRefs()));
            audit.setBidMatrix(toJson(bidMatrix));
            audit.setCreateTime(LocalDateTime.now());
            auditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("[audit] 分配审计落库失败 taskId={}: {}", task.getTaskId(), e.getMessage());
        }
    }

    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
