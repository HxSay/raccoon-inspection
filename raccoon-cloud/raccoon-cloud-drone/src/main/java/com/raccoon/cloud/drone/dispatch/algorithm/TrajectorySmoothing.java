package com.raccoon.cloud.drone.dispatch.algorithm;

import com.raccoon.cloud.drone.dto.GeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹平滑优化器：对 RRT* 输出做共线点剔除 + 三点滑动均值平滑。
 * <p>保持起点与终点不变，仅平滑中间点；既减少冗余航点，又避免飞行抖动。
 *
 * @author raccoon
 */
@Slf4j
@Component
public class TrajectorySmoothing {

    /** 共线判定阈值：相邻两段方向向量夹角余弦差值 */
    private static final double COLLINEAR_COS_DELTA = 0.005;

    /**
     * 平滑路径。
     *
     * @param raw 原始路径
     * @return 平滑后路径（不修改入参）
     */
    public List<GeoPoint> smooth(List<GeoPoint> raw) {
        if (raw == null || raw.size() < 3) {
            return raw == null ? new ArrayList<>() : new ArrayList<>(raw);
        }
        List<GeoPoint> cleaned = removeCollinear(raw);
        List<GeoPoint> smoothed = movingAverage(cleaned);
        log.debug("[smooth] in={} cleaned={} out={}", raw.size(), cleaned.size(), smoothed.size());
        return smoothed;
    }

    /** 剔除共线冗余点 */
    private List<GeoPoint> removeCollinear(List<GeoPoint> path) {
        List<GeoPoint> result = new ArrayList<>();
        result.add(path.get(0));
        for (int i = 1; i < path.size() - 1; i++) {
            GeoPoint prev = path.get(i - 1);
            GeoPoint cur = path.get(i);
            GeoPoint next = path.get(i + 1);
            if (!isCollinear(prev, cur, next)) {
                result.add(cur);
            }
        }
        result.add(path.get(path.size() - 1));
        return result;
    }

    /** 三点滑动均值平滑，保留首尾 */
    private List<GeoPoint> movingAverage(List<GeoPoint> path) {
        if (path.size() <= 2) {
            return new ArrayList<>(path);
        }
        List<GeoPoint> result = new ArrayList<>(path.size());
        result.add(path.get(0));
        for (int i = 1; i < path.size() - 1; i++) {
            GeoPoint a = path.get(i - 1);
            GeoPoint b = path.get(i);
            GeoPoint c = path.get(i + 1);
            double lng = (a.getLongitude() + b.getLongitude() * 2 + c.getLongitude()) / 4.0;
            double lat = (a.getLatitude() + b.getLatitude() * 2 + c.getLatitude()) / 4.0;
            double h = (val(a.getHeight()) + val(b.getHeight()) * 2 + val(c.getHeight())) / 4.0;
            result.add(new GeoPoint(lng, lat, h));
        }
        result.add(path.get(path.size() - 1));
        return result;
    }

    /** 三点夹角判定共线 */
    private boolean isCollinear(GeoPoint a, GeoPoint b, GeoPoint c) {
        double dx1 = b.getLongitude() - a.getLongitude();
        double dy1 = b.getLatitude() - a.getLatitude();
        double dx2 = c.getLongitude() - b.getLongitude();
        double dy2 = c.getLatitude() - b.getLatitude();
        double n1 = Math.hypot(dx1, dy1);
        double n2 = Math.hypot(dx2, dy2);
        if (n1 < 1e-9 || n2 < 1e-9) {
            return true;
        }
        double cos = (dx1 * dx2 + dy1 * dy2) / (n1 * n2);
        return Math.abs(1.0 - cos) < COLLINEAR_COS_DELTA;
    }

    private double val(Double v) {
        return v == null ? 0.0 : v;
    }
}
