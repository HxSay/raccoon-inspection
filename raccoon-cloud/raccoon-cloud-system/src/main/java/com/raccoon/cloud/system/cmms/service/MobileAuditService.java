package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.system.cmms.constants.InspectionWorkOrderStatus;
import com.raccoon.cloud.system.cmms.dto.InspectionWorkOrderIdRequest;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrder;
import com.raccoon.cloud.system.cmms.entity.InspectionWorkOrderDetail;
import com.raccoon.cloud.system.cmms.integration.DronePlanningClient;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderDetailMapper;
import com.raccoon.cloud.system.cmms.mapper.InspectionWorkOrderMapper;
import com.raccoon.common.dto.planning.WorkOrderAuditActionRequest;
import com.raccoon.common.dto.planning.WorkOrderAuditApproveResult;
import com.raccoon.common.dto.planning.WorkOrderAuditDetailDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 移动端审核：推送、审核通过/驳回、超时多级升级通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileAuditService {

    private final InspectionWorkOrderMapper orderMapper;
    private final InspectionWorkOrderDetailMapper detailMapper;
    private final InspectionWorkOrderService inspectionWorkOrderService;
    private final WorkOrderGenerateService workOrderGenerateService;
    private final DronePlanningClient dronePlanningClient;
    private final ObjectMapper objectMapper;

    @Value("${raccoon.planning.audit-timeout-hours:24}")
    private int auditTimeoutHours;

    @Value("${raccoon.planning.escalation-interval-minutes:60}")
    private int escalationIntervalMinutes;

    public IPage<InspectionWorkOrder> listPending(Long inspectorId, int page, int size) {
        QueryWrapper<InspectionWorkOrder> w = new QueryWrapper<>();
        w.eq("status", InspectionWorkOrderStatus.PENDING_AUDIT);
        if (inspectorId != null) {
            w.and(q -> q.eq("inspector_id", inspectorId).or().isNull("inspector_id"));
        }
        w.orderByAsc("audit_deadline");
        return orderMapper.selectPage(new Page<>(page, size), w);
    }

    public WorkOrderAuditDetailDTO detail(Long workOrderId) {
        InspectionWorkOrder order = orderMapper.selectById(workOrderId);
        if (order == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        List<InspectionWorkOrderDetail> steps = detailMapper.selectList(
                new QueryWrapper<InspectionWorkOrderDetail>()
                        .eq("order_id", workOrderId)
                        .orderByAsc("step_order"));

        WorkOrderAuditDetailDTO dto = new WorkOrderAuditDetailDTO();
        dto.setWorkOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setArea(order.getArea());
        dto.setStatus(order.getStatus());
        dto.setPriorityCode(order.getPriorityCode());
        dto.setDispatchTaskId(order.getDispatchTaskId());
        dto.setTerminalId(order.getTerminalId());
        dto.setTerminalName(order.getTerminalName());
        dto.setAssignReason(order.getAssignReason());
        dto.setPathPlanJson(order.getPathPlanJson());
        dto.setAuditDeadline(order.getAuditDeadline());
        dto.setRemark(order.getRemark());
        dto.setRejectReason(order.getRejectReason());
        dto.setSteps(steps.stream().map(this::toStepItem).collect(Collectors.toList()));
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkOrderAuditApproveResult approve(WorkOrderAuditActionRequest req) {
        InspectionWorkOrder order = requirePending(req.getWorkOrderId());
        order.setStatus(InspectionWorkOrderStatus.PENDING_ISSUE);
        if (req.getAuditorId() != null) {
            order.setInspectorId(req.getAuditorId());
        }
        if (StringUtils.hasText(req.getAuditorName())) {
            order.setInspectorName(req.getAuditorName().trim());
        }
        orderMapper.updateById(order);

        InspectionWorkOrderIdRequest issueReq = new InspectionWorkOrderIdRequest();
        issueReq.setId(order.getId());
        inspectionWorkOrderService.issue(issueReq);

        WorkOrderAuditApproveResult dispatchResult = dronePlanningClient.dispatchApproved(
                order.getDispatchTaskId(), order.getPlanningPayloadJson(), order.getTerminalId());
        if (!dispatchResult.isDispatched()) {
            log.warn("[MobileAudit] 审核通过但终端下发未确认 orderNo={} dispatchTaskId={} msg={}",
                    order.getOrderNo(), order.getDispatchTaskId(), dispatchResult.getMessage());
            throw new IllegalStateException(dispatchResult.getMessage() != null
                    ? dispatchResult.getMessage()
                    : "正式下发失败，请确认 drone 服务在线且工单含规划载荷");
        }
        pushApp(order, "审核通过，巡检任务已正式下发至终端");
        log.info("[MobileAudit] 工单审核通过 orderNo={} dispatchTaskId={}", order.getOrderNo(), order.getDispatchTaskId());
        return dispatchResult;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(WorkOrderAuditActionRequest req) {
        if (!StringUtils.hasText(req.getRejectReason())) {
            throw new IllegalArgumentException("请填写驳回原因");
        }
        InspectionWorkOrder order = requirePending(req.getWorkOrderId());
        order.setStatus(InspectionWorkOrderStatus.AUDIT_REJECTED);
        order.setRejectReason(req.getRejectReason().trim());
        orderMapper.updateById(order);

        pushApp(order, "审核驳回：" + order.getRejectReason());
        boolean replanned = dronePlanningClient.replanRejected(order.getId(), order.getDispatchTaskId(), order.getPlanningPayloadJson());
        if (!replanned) {
            log.warn("[MobileAudit] 驳回后自动重规划失败 workOrderId={}", order.getId());
        }
        log.info("[MobileAudit] 工单审核驳回 orderNo={} reason={}", order.getOrderNo(), order.getRejectReason());
    }

    public void notifyOnSubmit(InspectionWorkOrder order) {
        if (order == null) {
            return;
        }
        boolean urgent = isUrgent(order.getPriorityCode());
        pushApp(order, "新巡检工单待审核：" + order.getOrderNo());
        if (urgent) {
            pushSms(order, "【紧急巡检】工单 " + order.getOrderNo() + " 待审核，截止时间 "
                    + (order.getAuditDeadline() != null ? order.getAuditDeadline() : "尽快"));
        }
    }

    @Scheduled(fixedDelay = 120_000)
    public void scanAuditTimeout() {
        LocalDateTime now = LocalDateTime.now();
        List<InspectionWorkOrder> overdue = orderMapper.selectList(
                new QueryWrapper<InspectionWorkOrder>()
                        .eq("status", InspectionWorkOrderStatus.PENDING_AUDIT)
                        .lt("audit_deadline", now));
        for (InspectionWorkOrder order : overdue) {
            escalate(order, 1);
        }
    }

    private void escalate(InspectionWorkOrder order, int level) {
        String msg = "【审核超时升级 L" + level + "】工单 " + order.getOrderNo()
                + " 已超过审核截止时间，请管理员处理";
        pushApp(order, msg);
        pushSms(order, msg);
        LocalDateTime next = LocalDateTime.now().plusMinutes((long) escalationIntervalMinutes * level);
        order.setAuditDeadline(next);
        order.setRemark(appendRemark(order.getRemark(), "audit_escalation_L" + level));
        orderMapper.updateById(order);
        log.warn("[MobileAudit] 审核超时升级 L{} orderNo={}", level, order.getOrderNo());
    }

    private InspectionWorkOrder requirePending(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("缺少工单ID");
        }
        InspectionWorkOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        if (order.getStatus() != InspectionWorkOrderStatus.PENDING_AUDIT) {
            throw new IllegalArgumentException("工单非待审核状态");
        }
        return order;
    }

    private WorkOrderAuditDetailDTO.AuditStepItem toStepItem(InspectionWorkOrderDetail d) {
        WorkOrderAuditDetailDTO.AuditStepItem item = new WorkOrderAuditDetailDTO.AuditStepItem();
        item.setStepOrder(d.getStepOrder());
        item.setType(d.getStepType());
        item.setTarget(d.getTarget());
        item.setDescription(d.getDescription());
        item.setDeviceName(d.getDeviceName());
        item.setCheckItem(d.getCheckItem());
        item.setUnit(d.getUnit());
        item.setStandardRange(formatRange(d.getStandardMin(), d.getStandardMax()));
        return item;
    }

    private String formatRange(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return min + " ~ " + max;
        }
        if (min != null) {
            return ">= " + min;
        }
        return "<= " + max;
    }

    private boolean isUrgent(String priorityCode) {
        if (!StringUtils.hasText(priorityCode)) {
            return false;
        }
        String p = priorityCode.trim().toUpperCase();
        return "URGENT".equals(p) || "CRITICAL".equals(p) || "HIGH".equals(p)
                || "EMERGENCY".equals(p);
    }

    private void pushApp(InspectionWorkOrder order, String content) {
        log.info("[APP推送] inspectorId={} orderNo={} msg={}",
                order.getInspectorId(), order.getOrderNo(), content);
    }

    private void pushSms(InspectionWorkOrder order, String content) {
        log.info("[短信推送] inspectorId={} orderNo={} msg={}",
                order.getInspectorId(), order.getOrderNo(), content);
    }

    private String appendRemark(String existing, String line) {
        if (!StringUtils.hasText(existing)) {
            return line;
        }
        return existing + " | " + line;
    }
}
