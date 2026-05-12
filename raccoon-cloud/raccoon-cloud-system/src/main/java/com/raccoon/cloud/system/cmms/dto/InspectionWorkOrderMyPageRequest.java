package com.raccoon.cloud.system.cmms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的工单分页：按当前登录人 inspectorId 过滤（若未登录则须传 inspectorId）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionWorkOrderMyPageRequest extends CmmsPageRequest {
    /** pending=待执行(2) running=执行中(3) done=已完成(4) */
    @NotBlank
    private String tab;
    /** 可选，未登录调试时传入 */
    private Long inspectorId;
}
