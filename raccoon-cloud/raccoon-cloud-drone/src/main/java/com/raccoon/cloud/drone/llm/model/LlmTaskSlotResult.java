package com.raccoon.cloud.drone.llm.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM / 规则解析出的槽位结果（中间态，尚未转为下发 JSON）。
 */
@Data
public class LlmTaskSlotResult {

    private String taskType;
    private String areaName;
    private List<String> deviceNames = new ArrayList<>();
    private String priority;
    private String planTime;
    private String remark;

    /** 解析来源：LLM / RULE */
    private String parseSource;

    /**
     * 是否巡检区域内全部设备（口语：「所有杆塔」「全部设备」）。
     * 为 true 时 {@link com.raccoon.cloud.drone.llm.service.ResultCheckAndFillService}
     * 将按 mapId 自动展开设备列表，不再追问 deviceNames。
     */
    private Boolean inspectAllDevices;

    /**
     * 建议出动的无人机数量（由 LLM 根据任务覆盖范围给出，后端按真实可用机数 / 设备数裁剪）。
     */
    private Integer recommendedDrones;

    /**
     * 选择该出动架数的理由（LLM 生成，缺省时由后端按设备数 / 可用机数兜底生成），用于在巡检 Agent 面板展示。
     */
    private String fleetReason;

    /**
     * 任务语义要求的传感器（如「热成像复巡」→ THERMAL_IR）。
     * 作为调度拍卖的硬约束之一，缺省为空。
     */
    private List<String> requiredSensors = new ArrayList<>();
}
