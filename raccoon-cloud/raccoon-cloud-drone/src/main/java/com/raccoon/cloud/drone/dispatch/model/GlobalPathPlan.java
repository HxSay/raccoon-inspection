package com.raccoon.cloud.drone.dispatch.model;

import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.PhotoWaypoint;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局路径规划输出。
 * <p>由 GlobalPathPlanService（RRT* + TSP + 轨迹优化）产出，是作业清单下发前的最后一步。
 *
 * @author raccoon
 */
@Data
@Accessors(chain = true)
public class GlobalPathPlan {

    /** 起飞点 */
    private GeoPoint takeoff;

    /** 降落点 */
    private GeoPoint landing;

    /** 飞行航点（按顺序） */
    private List<GeoPoint> waypoints = new ArrayList<>();

    /** 拍照航点（含绑定设备） */
    private List<PhotoWaypoint> photoWaypoints = new ArrayList<>();

    /** 设备访问顺序（TSP 排序结果） */
    private List<Long> deviceVisitOrder = new ArrayList<>();

    /** 总航程（米） */
    private double distanceM;

    /** 预估耗时（秒） */
    private int durationSec;

    /** 预估耗电（%） */
    private float batteryPct;

    /** 使用的规划算法 */
    private String algorithm;

    /** RRT* 采样次数（调试用） */
    private int sampleCount;

    /** 规划耗时 ms */
    private long elapsedMs;
}
