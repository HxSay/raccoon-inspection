package com.raccoon.cloud.drone.dispatch.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拍卖分配结果。
 * <p>价格矩阵保留所有 (terminalId, taskId) 的竞拍价快照，便于后续审计或回放调试。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class AssignmentResult {

    /** taskId -> terminalId */
    private Map<String, Long> taskTerminalMap = new HashMap<>();

    /** terminalId -> 已分配任务数（容量约束计数） */
    private Map<Long, Integer> terminalTaskCount = new HashMap<>();

    /** 全量价格矩阵：taskId -> 该任务上所有终端的竞拍价 */
    private Map<String, List<BidPrice>> priceMatrix = new HashMap<>();

    /** 未分配的任务（容量耗尽或全部终端被排除） */
    private List<String> unassignedTaskIds = new ArrayList<>();

    /** 算法迭代耗时 ms */
    private long elapsedMs;
}
