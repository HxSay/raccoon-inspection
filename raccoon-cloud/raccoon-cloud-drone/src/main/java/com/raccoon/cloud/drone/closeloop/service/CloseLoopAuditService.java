package com.raccoon.cloud.drone.closeloop.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.closeloop.dto.CloseLoopReportRequest;
import com.raccoon.cloud.drone.closeloop.dto.CloseLoopResult;
import com.raccoon.cloud.drone.closeloop.dto.IntegrityVerifyResult;
import com.raccoon.cloud.drone.closeloop.dto.RescheduleResult;
import com.raccoon.cloud.drone.closeloop.entity.TaskCloseLoopAuditEntity;
import com.raccoon.cloud.drone.closeloop.mapper.TaskCloseLoopAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 步骤7：闭环审计与合规导出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloseLoopAuditService {

    private final TaskCloseLoopAuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    public void persist(CloseLoopReportRequest req, CloseLoopResult result,
                        IntegrityVerifyResult verify, RescheduleResult reschedule) {
        TaskCloseLoopAuditEntity e = new TaskCloseLoopAuditEntity();
        e.setTaskId(req.getTaskId());
        e.setWorkOrderId(req.getWorkOrderId());
        e.setTaskName(result.getReport() != null ? result.getReport().getTaskName() : req.getTaskName());
        e.setCloseStatus(result.getCloseStatus());
        e.setCompletionRate(result.getCompletionRate());
        e.setMissedItemsJson(toJson(buildMissedItems(verify)));
        e.setReportJson(toJson(result.getReport()));
        e.setReportSummary(result.getReport() != null ? result.getReport().getExecutiveSummary() : null);
        if (reschedule != null && reschedule.isSuccess() && reschedule.getReinspectTaskId() != null) {
            e.setRescheduleTaskId(reschedule.getReinspectTaskId());
        }
        e.setRescheduleCount((req.getRescheduleCount() != null ? req.getRescheduleCount() : 0)
                + (reschedule != null && reschedule.isNeeded() ? 1 : 0));
        e.setCloseTime(LocalDateTime.now());
        try {
            int inserted = auditMapper.insert(e);
            if (inserted != 1) {
                throw new IllegalStateException("close loop audit insert affected rows=" + inserted
                        + ", taskId=" + req.getTaskId());
            }
            return;
        } catch (Exception ex) {
            log.error("[closeloop-audit] 写审计失败 taskId={}", req.getTaskId(), ex);
            throw new IllegalStateException("close loop audit persist failed for taskId=" + req.getTaskId(), ex);
        }
    }

    public List<TaskCloseLoopAuditEntity> recent(int limit) {
        return listForExport(limit);
    }

    public List<TaskCloseLoopAuditEntity> listForExport(int limit) {
        return auditMapper.selectList(new QueryWrapper<TaskCloseLoopAuditEntity>()
                .orderByDesc("close_time", "id")
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
    }

    public TaskCloseLoopAuditEntity latestByTask(String taskId) {
        return auditMapper.selectOne(new QueryWrapper<TaskCloseLoopAuditEntity>()
                .eq("task_id", taskId)
                .orderByDesc("close_time", "id")
                .last("LIMIT 1"));
    }

    private Map<String, Object> buildMissedItems(IntegrityVerifyResult verify) {
        Map<String, Object> m = new HashMap<>();
        if (verify == null) {
            return m;
        }
        m.put("missedDeviceIds", verify.getMissedDeviceIds());
        m.put("missedWaypointCount", verify.getMissedWaypointCount());
        m.put("dataGaps", verify.getDataGaps());
        m.put("trackComplete", verify.isTrackComplete());
        return m;
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
}
