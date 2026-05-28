package com.raccoon.cloud.drone.dispatch.dto;

import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 调度决策快照：供中央 Agent 一次性拉取全场景终端状态。
 *
 * @author raccoon
 */
@Data
public class DispatchSnapshotVO {

    /** 场景地图 ID */
    private Long mapId;

    /** 场景名称 */
    private String mapName;

    /** 场景类型 */
    private String sceneType;

    /** 终端总数 */
    private int totalTerminals;

    /** 在线终端数 */
    private int onlineTerminals;

    /** 可承接任务终端数（在线 + 非故障 + 电量充足） */
    private int availableTerminals;

    /** 终端实时状态列表 */
    private List<TerminalState> terminals = new ArrayList<>();

    /** 快照生成时间 */
    private LocalDateTime snapshotAt;
}
