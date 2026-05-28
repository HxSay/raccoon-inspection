package com.raccoon.cloud.drone.dispatch.model;

import com.raccoon.cloud.drone.dto.GeoPoint;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 多机时空路径冲突描述。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class ConflictInfo {

    /** 冲突类型 SPATIAL（空间近距）/ TEMPORAL（同时段同区域）/ RESOURCE（设备占用） */
    private String conflictType;

    /** 冲突涉及的终端 ID */
    private List<Long> terminalIds = new ArrayList<>();

    /** 冲突涉及的任务 ID */
    private List<String> taskIds = new ArrayList<>();

    /** 冲突点位（可空，仅 SPATIAL 类型有意义） */
    private GeoPoint conflictPoint;

    /** 时间冲突时段（开始时间偏移秒，相对调度起点） */
    private Integer conflictStartOffsetSec;
    private Integer conflictEndOffsetSec;

    /** 冲突最小间距（米） */
    private Double minDistanceM;

    /** 解决策略 RESCHEDULE / REROUTE / SWAP */
    private String resolution;

    /** 描述 */
    private String description;
}
