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
}
