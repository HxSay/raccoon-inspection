package com.raccoon.cloud.drone.dispatch.model.knowledge;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Neo4j 查询到的设备上下游拓扑节点（影响范围分析，用于复巡优先级）。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class TopologyNode {

    /** 源设备 ID（任务关联设备） */
    private Long sourceId;

    /** 下游设备 ID */
    private Long targetId;

    /** 下游设备名称 */
    private String targetName;

    /** 下游设备关键等级（0~1 或文本枚举映射后） */
    private Double criticalLevel;
}
