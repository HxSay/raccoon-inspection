package com.raccoon.cloud.drone.dispatch.algorithm;

import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.util.GeoPathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 点位访问顺序 TSP 优化（贪心 + 2-opt 局部搜索）。
 * <p>设备规模较小时收敛迅速，能在 200ms 时延预算内完成 30+ 点位排序。
 *
 * @author raccoon
 */
@Slf4j
@Component
public class TspOrderOptimizer {

    /** 2-opt 最大迭代次数，超过即终止以满足时延 */
    private static final int MAX_2OPT_ITERATIONS = 200;

    /**
     * 计算访问顺序。
     *
     * @param origin  起点
     * @param devices 待访问设备
     * @return 排序后的设备列表（同元素，仅顺序变更）
     */
    public List<UavInspectionDevice> optimize(GeoPoint origin, List<UavInspectionDevice> devices) {
        if (devices == null || devices.size() <= 1 || origin == null) {
            return devices == null ? new ArrayList<>() : new ArrayList<>(devices);
        }
        long start = System.currentTimeMillis();

        List<UavInspectionDevice> route = nearestNeighbor(origin, devices);
        twoOpt(route, origin);

        log.debug("[tsp] 完成 size={} elapsedMs={}", route.size(),
                System.currentTimeMillis() - start);
        return route;
    }

    /** 最近邻贪心构造初始解 */
    private List<UavInspectionDevice> nearestNeighbor(GeoPoint origin, List<UavInspectionDevice> devices) {
        List<UavInspectionDevice> remaining = new ArrayList<>(devices);
        List<UavInspectionDevice> route = new ArrayList<>(devices.size());
        Set<Integer> used = new HashSet<>();
        GeoPoint cur = origin;
        while (route.size() < devices.size()) {
            int bestIdx = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                if (used.contains(i)) {
                    continue;
                }
                UavInspectionDevice d = remaining.get(i);
                GeoPoint p = toGeoPoint(d);
                if (p == null) {
                    continue;
                }
                double dist = GeoPathUtils.segmentLengthMeters(cur, p);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) {
                break;
            }
            used.add(bestIdx);
            UavInspectionDevice next = remaining.get(bestIdx);
            route.add(next);
            cur = toGeoPoint(next);
        }
        return route;
    }

    /** 2-opt 局部翻转，逐步缩短总路径 */
    private void twoOpt(List<UavInspectionDevice> route, GeoPoint origin) {
        boolean improved = true;
        int iter = 0;
        double bestLength = totalLength(origin, route);
        while (improved && iter < MAX_2OPT_ITERATIONS) {
            improved = false;
            iter++;
            for (int i = 0; i < route.size() - 1; i++) {
                for (int k = i + 1; k < route.size(); k++) {
                    reverseSegment(route, i, k);
                    double candidate = totalLength(origin, route);
                    if (candidate + 1e-3 < bestLength) {
                        bestLength = candidate;
                        improved = true;
                    } else {
                        reverseSegment(route, i, k);
                    }
                }
            }
        }
    }

    private void reverseSegment(List<UavInspectionDevice> route, int i, int k) {
        while (i < k) {
            UavInspectionDevice tmp = route.get(i);
            route.set(i, route.get(k));
            route.set(k, tmp);
            i++;
            k--;
        }
    }

    private double totalLength(GeoPoint origin, List<UavInspectionDevice> route) {
        double sum = 0.0;
        GeoPoint prev = origin;
        for (UavInspectionDevice d : route) {
            GeoPoint p = toGeoPoint(d);
            if (p == null) {
                continue;
            }
            sum += GeoPathUtils.segmentLengthMeters(prev, p);
            prev = p;
        }
        return sum;
    }

    private GeoPoint toGeoPoint(UavInspectionDevice d) {
        if (d == null || d.getLongitude() == null || d.getLatitude() == null) {
            return null;
        }
        return new GeoPoint(d.getLongitude(), d.getLatitude(),
                d.getHeight() == null ? 0.0 : d.getHeight());
    }
}
