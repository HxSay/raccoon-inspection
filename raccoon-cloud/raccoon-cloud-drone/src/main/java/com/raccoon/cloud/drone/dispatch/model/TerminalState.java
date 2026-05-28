package com.raccoon.cloud.drone.dispatch.model;

import com.raccoon.cloud.drone.dto.GeoPoint;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 终端实时状态聚合视图。
 * <p>由 TerminalStateService 通过【Redis 优先 → 数据库兜底】聚合而成，包含五大维度：
 * 位置、电量、负载、运行、作业范围（静态），并内嵌量化能力模型 {@link TerminalCapacity}。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class TerminalState {

    /** 终端 ID（=uav_info.id） */
    private Long terminalId;

    /** 终端业务编码 */
    private String terminalCode;

    /** 终端名称 */
    private String terminalName;

    /** 终端类型 UAV/ROBOT_DOG/GROUND_ROBOT/WHEELED */
    private String terminalType;

    /** 所属场景地图 ID */
    private Long mapId;

    /** 场景类型：patrol/substation/thermal 等 */
    private String sceneType;

    /** 实时位置（经纬度 + 高度），可能为空（未上报） */
    private GeoPoint position;

    /** 位置最近更新时间 */
    private LocalDateTime positionAt;

    /** 剩余电量 % */
    private Float batteryPct;

    /** 预计剩余续航分钟数 */
    private Integer enduranceMin;

    /** 当前已分配任务数 */
    private Integer assignedTaskCount;

    /** 边缘端 CPU 负载 % */
    private Float cpuPct;

    /** 边缘端内存负载 % */
    private Float memoryPct;

    /** 是否在线（由 InspectionRobotRuntimeService 计算，含心跳超时判定） */
    private boolean online;

    /** 飞行/作业阶段 STANDBY/FLYING/RTH/LANDING/OFFLINE */
    private String flightStatus;

    /** 故障状态 NONE/WARNING/FAULT */
    private String faultStatus;

    /** 故障描述 */
    private String faultMessage;

    /** 静态作业范围（可覆盖区域文字描述） */
    private String workRangeDesc;

    /** 能力模型 */
    private TerminalCapacity capacity;

    /** 聚合时间戳 */
    private LocalDateTime aggregatedAt;

    /**
     * 是否可承接新任务：在线、未严重故障、电量充足、未达任务容量。
     *
     * @param batteryThreshold 电量阈值（%）
     * @param maxConcurrent    并发任务上限
     * @return 是否可承接
     */
    public boolean canAcceptTask(float batteryThreshold, int maxConcurrent) {
        if (!online) {
            return false;
        }
        if ("FAULT".equalsIgnoreCase(faultStatus)) {
            return false;
        }
        if (batteryPct != null && batteryPct < batteryThreshold) {
            return false;
        }
        int taskCount = assignedTaskCount == null ? 0 : assignedTaskCount;
        return taskCount < maxConcurrent;
    }
}
