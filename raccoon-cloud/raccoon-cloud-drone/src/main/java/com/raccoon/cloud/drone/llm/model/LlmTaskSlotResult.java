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
}
