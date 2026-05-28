package com.raccoon.cloud.drone.dispatch.dto;

import com.raccoon.cloud.drone.dto.GeoPoint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度中枢对外受理的原始任务请求。
 * <p>支持两种入参形态：
 * <ul>
 *   <li>结构化：填写 {@link #mapId}/{@link #deviceNames} 等字段</li>
 *   <li>自然语言：仅填写 {@link #userInput}，由 TaskParseService 接入 NLP 解析后回填</li>
 * </ul>
 *
 * @author raccoon
 */
@Data
public class DispatchTaskRequest {

    /** 外部业务请求 ID，用于幂等校验，可空 */
    private String requestId;

    /** 自然语言输入；若填写则优先走 NLP 解析 */
    private String userInput;

    /** 任务类型，未填则取 REGULAR */
    private String taskType;

    /** 优先级（legacy/五档），未填则按五维评分推算 */
    private String priority;

    /** 场景地图 ID（结构化入参） */
    private Long mapId;

    /** 巡检区域名称（结构化入参） */
    private String areaName;

    /** 巡检设备名称列表，最多 100 个 */
    @Size(max = 100)
    private List<String> deviceNames = new ArrayList<>();

    /** 巡检设备 ID 列表（高级用法，跳过名称解析） */
    @Size(max = 100)
    private List<Long> deviceIds = new ArrayList<>();

    /** 自定义点位（可选） */
    private List<GeoPoint> waypoints = new ArrayList<>();

    /** 关键等级 0~1 */
    private Double criticalLevel;

    /** 风险等级 0~1 */
    private Double riskLevel;

    /** 历史故障率 0~1 */
    private Double faultHistoryRate;

    /** 距上次巡检间隔小时 */
    private Double inspectionIntervalHours;

    /** 计划时间，未填则立即执行 */
    private LocalDateTime planTime;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 备注 */
    private String remark;

    /** 限定优先终端 ID，仅在该集合内分配（可空） */
    private List<Long> preferredTerminalIds = new ArrayList<>();

    /** 行业扩展字段 */
    private Map<String, Object> extension = new HashMap<>();

    /** 是否需要孪生预演（关闭时跳过 PPO，降低时延） */
    @NotNull
    private Boolean enableSimulation = Boolean.TRUE;

    /** 是否立即下发到边缘 Agent */
    @NotNull
    private Boolean autoDispatch = Boolean.TRUE;
}
