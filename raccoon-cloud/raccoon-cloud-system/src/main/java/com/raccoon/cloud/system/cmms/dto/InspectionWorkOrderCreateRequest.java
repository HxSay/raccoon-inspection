package com.raccoon.cloud.system.cmms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建巡检工单请求体（含步骤列表，与前端 JSON 对齐）。
 */
@Data
public class InspectionWorkOrderCreateRequest {

    @NotBlank
    private String area;
    @NotNull
    private Integer shiftType;
    @NotNull
    private Long inspectorId;
    private String inspectorName;
    @NotBlank
    private String planStartTime;
    @NotBlank
    private String planEndTime;
    private String remark;

    /** 来源巡检计划（可选，与 inspection_task.plan_id 一致） */
    private Long planId;
    /** 关联巡检任务 */
    private Long taskId;

    @NotEmpty
    @Valid
    private List<InspectionWorkOrderStepDraft> steps;

    @Data
    public static class InspectionWorkOrderStepDraft {
        @NotNull
        private Integer stepOrder;
        /** path / stop / collect / report */
        @NotBlank
        private String type;
        private String target;
        private String description;
        private Long deviceId;
        private String deviceName;
        private String checkItem;
        private BigDecimal standardMin;
        private BigDecimal standardMax;
        private String unit;
        /** 不入库，仅前端展示；接口返回时原样回传 */
        private String detectionMethod;
    }
}
