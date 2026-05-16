package com.raccoon.cloud.drone.util;

import com.raccoon.cloud.drone.dto.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public final class GeoPathUtils {

    private static final double EARTH_RADIUS_M = 6371000.0;

    private GeoPathUtils() {
    }

    public static List<GeoPoint> buildOrderedPath(
            GeoPoint start,
            GeoPoint end,
            List<GeoPoint> middle
    ) {
        List<GeoPoint> path = new ArrayList<>();
        path.add(start);
        if (middle != null) {
            path.addAll(middle);
        }
        path.add(end);
        return path;
    }

    /** 三维折线总长度（米）：水平大圆距离 + 高度差 */
    public static double totalPathLengthMeters(List<GeoPoint> ordered) {
        if (ordered == null || ordered.size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 1; i < ordered.size(); i++) {
            sum += segmentLengthMeters(ordered.get(i - 1), ordered.get(i));
        }
        return sum;
    }

    public static double segmentLengthMeters(GeoPoint a, GeoPoint b) {
        double horiz = haversineMeters(
                a.getLatitude(), a.getLongitude(),
                b.getLatitude(), b.getLongitude()
        );
        double dh = (b.getHeight() == null ? 0.0 : b.getHeight())
                - (a.getHeight() == null ? 0.0 : a.getHeight());
        return Math.sqrt(horiz * horiz + dh * dh);
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double s = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(s)));
    }
}
