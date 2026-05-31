package com.raccoon.common.dto.planning;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 移动端审核详情 DTO：工单信息、分配设备、规划路径、标准巡检步骤。
 */
@Data
public class WorkOrderAuditDetailDTO {

    private Long workOrderId;
    private String orderNo;
    private String area;
    private Integer status;
    private String priorityCode;
    private String dispatchTaskId;
    private Long terminalId;
    private String terminalName;
    private String assignReason;
    private String pathPlanJson;
    private LocalDateTime auditDeadline;
    private String remark;
    private String rejectReason;

    private List<AuditStepItem> steps = new ArrayList<>();

    @Data
    public static class AuditStepItem {
        private Integer stepOrder;
        private String type;
        private String target;
        private String description;
        private String deviceName;
        private String checkItem;
        private String standardRange;
        private String unit;
    }
}
