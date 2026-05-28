package com.raccoon.cloud.drone.dispatch.algorithm;

import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.util.GeoPathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 改进 RRT*（Rapidly-exploring Random Tree Star）三维路径规划器。
 * <p>本实现保留 RRT* 的核心步骤：随机采样 → 最近节点连接 → 邻域重连优化 → 回溯成路径。
 * 出于轻量与编译一致性的考虑，当前禁飞区接口通过 {@link ObstacleChecker} 暴露，
 * 默认实现 {@link NoObstacleChecker} 返回 false（无障碍），可由调用方注入业务实现。
 *
 * @author raccoon
 */
@Slf4j
@Component
public class RrtStarPlanner {

    /** 障碍/禁飞区判断器；默认认为路径全部可达 */
    public interface ObstacleChecker {
        boolean isBlocked(GeoPoint from, GeoPoint to);
    }

    /** 默认实现：无障碍 */
    public static final class NoObstacleChecker implements ObstacleChecker {
        @Override
        public boolean isBlocked(GeoPoint from, GeoPoint to) {
            return false;
        }
    }

    private final Random random = new Random();

    /**
     * 规划从 start 到 goal 的三维巡检路径。
     *
     * @param start    起点（非空）
     * @param goal     目标点（非空）
     * @param obstacle 障碍判断器，可空（视为无障碍）
     * @return 路径序列；至少包含 {@code [start, goal]}
     */
    public List<GeoPoint> plan(GeoPoint start, GeoPoint goal, ObstacleChecker obstacle) {
        if (start == null || goal == null) {
            throw new IllegalArgumentException("起点/目标点不能为空");
        }
        ObstacleChecker checker = obstacle != null ? obstacle : new NoObstacleChecker();

        // 路径直连且无障碍：直接返回，避免无意义采样
        if (!checker.isBlocked(start, goal)) {
            return new ArrayList<>(List.of(start, goal));
        }

        List<Node> tree = new ArrayList<>();
        tree.add(new Node(start, null, 0.0));
        Node goalNode = null;

        for (int i = 0; i < DispatchConstants.RRT_MAX_SAMPLES && goalNode == null; i++) {
            GeoPoint sample = randomPointBetween(start, goal);
            Node nearest = findNearest(tree, sample);
            GeoPoint stepTarget = step(nearest.point, sample, DispatchConstants.RRT_STEP_M);
            if (checker.isBlocked(nearest.point, stepTarget)) {
                continue;
            }
            double cost = nearest.cost + GeoPathUtils.segmentLengthMeters(nearest.point, stepTarget);
            Node newNode = new Node(stepTarget, nearest, cost);
            tree.add(newNode);

            // 邻域重连：找到更短路径父节点
            rewire(tree, newNode, checker);

            // 接近目标：尝试直接连接
            if (GeoPathUtils.segmentLengthMeters(stepTarget, goal) < DispatchConstants.RRT_STEP_M
                    && !checker.isBlocked(stepTarget, goal)) {
                goalNode = new Node(goal, newNode,
                        newNode.cost + GeoPathUtils.segmentLengthMeters(stepTarget, goal));
                tree.add(goalNode);
            }
        }

        if (goalNode == null) {
            log.warn("[rrt] 未在 {} 次采样内连通，使用直连兜底 startHeight={} goalHeight={}",
                    DispatchConstants.RRT_MAX_SAMPLES, start.getHeight(), goal.getHeight());
            return new ArrayList<>(List.of(start, goal));
        }
        return backtrack(goalNode);
    }

    /** 在 start-goal 包围盒内随机采样（含 ±20% 外扩） */
    private GeoPoint randomPointBetween(GeoPoint start, GeoPoint goal) {
        double lngLo = Math.min(start.getLongitude(), goal.getLongitude());
        double lngHi = Math.max(start.getLongitude(), goal.getLongitude());
        double latLo = Math.min(start.getLatitude(), goal.getLatitude());
        double latHi = Math.max(start.getLatitude(), goal.getLatitude());
        double hLo = Math.min(start.getHeight(), goal.getHeight());
        double hHi = Math.max(start.getHeight(), goal.getHeight());
        double lng = lngLo + (lngHi - lngLo + 1e-6) * (random.nextDouble() * 1.4 - 0.2);
        double lat = latLo + (latHi - latLo + 1e-6) * (random.nextDouble() * 1.4 - 0.2);
        double h = hLo + (hHi - hLo + 1e-6) * (random.nextDouble() * 1.4 - 0.2);
        return new GeoPoint(lng, lat, Math.max(0.0, h));
    }

    private Node findNearest(List<Node> tree, GeoPoint sample) {
        Node nearest = tree.get(0);
        double bestDist = GeoPathUtils.segmentLengthMeters(nearest.point, sample);
        for (int i = 1; i < tree.size(); i++) {
            Node n = tree.get(i);
            double d = GeoPathUtils.segmentLengthMeters(n.point, sample);
            if (d < bestDist) {
                bestDist = d;
                nearest = n;
            }
        }
        return nearest;
    }

    /** 朝 target 方向走 stepM 米；若距离不足直接返回 target */
    private GeoPoint step(GeoPoint from, GeoPoint target, double stepM) {
        double dist = GeoPathUtils.segmentLengthMeters(from, target);
        if (dist <= stepM) {
            return target;
        }
        double ratio = stepM / dist;
        double lng = from.getLongitude() + (target.getLongitude() - from.getLongitude()) * ratio;
        double lat = from.getLatitude() + (target.getLatitude() - from.getLatitude()) * ratio;
        double h = (from.getHeight() == null ? 0.0 : from.getHeight())
                + ((target.getHeight() == null ? 0.0 : target.getHeight())
                - (from.getHeight() == null ? 0.0 : from.getHeight())) * ratio;
        return new GeoPoint(lng, lat, h);
    }

    /**
     * 邻域重连：若邻域内某节点经由 newNode 可获得更低代价，则更新其父指针。
     */
    private void rewire(List<Node> tree, Node newNode, ObstacleChecker checker) {
        double radius = DispatchConstants.RRT_STEP_M * 1.5;
        for (Node n : tree) {
            if (n == newNode || n == newNode.parent) {
                continue;
            }
            double d = GeoPathUtils.segmentLengthMeters(newNode.point, n.point);
            if (d > radius) {
                continue;
            }
            double potential = newNode.cost + d;
            if (potential < n.cost && !checker.isBlocked(newNode.point, n.point)) {
                n.parent = newNode;
                n.cost = potential;
            }
        }
    }

    /** 从 goal 回溯到 start，返回正向序列 */
    private List<GeoPoint> backtrack(Node goalNode) {
        List<GeoPoint> path = new ArrayList<>();
        Node cur = goalNode;
        while (cur != null) {
            path.add(0, cur.point);
            cur = cur.parent;
        }
        return path;
    }

    /** 内部树节点 */
    private static final class Node {
        private final GeoPoint point;
        private Node parent;
        private double cost;

        Node(GeoPoint point, Node parent, double cost) {
            this.point = point;
            this.parent = parent;
            this.cost = cost;
        }
    }
}
