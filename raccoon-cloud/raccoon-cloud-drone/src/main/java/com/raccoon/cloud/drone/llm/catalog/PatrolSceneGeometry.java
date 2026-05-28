package com.raccoon.cloud.drone.llm.catalog;

import com.raccoon.cloud.drone.dto.GeoPoint;

/**
 * 与 raccoon-drone-sim 输电巡检场景几何一致（scenePatrolLayout + sceneToGps）。
 */
public final class PatrolSceneGeometry {

    public static final double BASE_LAT = 30.5;
    public static final double BASE_LON = 104.0;
    public static final double LAT_PER_M = 1.0 / 111_320.0;
    public static final double LON_PER_M = 1.0 / (111_320.0 * Math.cos(Math.toRadians(30.0)));

    public static final double NEST_X = 0;
    public static final double NEST_Y = 3;
    public static final double NEST_Z = 40;

    public static final double TOWER_SPAN_M = 200;
    public static final double CORRIDOR_Z0 = -35;
    public static final double PHOTO_Z_OFFSET = -12;

    /** 与 PATROL_TOWER_XS 一致 */
    private static final double[] TOWER_XS = {
            NEST_X - 2 * TOWER_SPAN_M,
            NEST_X - TOWER_SPAN_M,
            NEST_X,
            NEST_X + TOWER_SPAN_M,
            NEST_X + 2 * TOWER_SPAN_M
    };

    /** 与 PATROL_TOWER_HEIGHTS 一致 */
    private static final double[] TOWER_HEIGHTS = {58, 64, 60, 62, 59};

    private PatrolSceneGeometry() {
    }

    public static GeoPoint sceneToGeo(double x, double y, double z) {
        double lat = BASE_LAT + x * LAT_PER_M;
        double lon = BASE_LON + z * LON_PER_M;
        return new GeoPoint(lon, lat, y);
    }

    public static GeoPoint nestTakeoff() {
        return sceneToGeo(NEST_X, NEST_Y, NEST_Z);
    }

    /** 起飞后爬升过渡点（机巢上空） */
    public static GeoPoint nestTransit() {
        return sceneToGeo(NEST_X, 35, NEST_Z);
    }

    /**
     * @param towerIndex 1-based，对应「杆塔1」…「杆塔5」
     */
    public static double towerSceneX(int towerIndex) {
        int i = towerIndex - 1;
        if (i < 0 || i >= TOWER_XS.length) {
            return NEST_X;
        }
        return TOWER_XS[i];
    }

    public static GeoPoint towerPhotoPoint(int towerIndex) {
        int i = towerIndex - 1;
        if (i < 0 || i >= TOWER_XS.length) {
            throw new IllegalArgumentException("杆塔序号超出场景范围: " + towerIndex);
        }
        double h = TOWER_HEIGHTS[i];
        double photoY = Math.max(portalTowerMiddleArmWorldY(h) + 8, 36);
        double z = CORRIDOR_Z0 + PHOTO_Z_OFFSET;
        return sceneToGeo(TOWER_XS[i], photoY, z);
    }

    /** 与仿真 portalTowerMiddleArmWorldY 一致 */
    private static double portalTowerMiddleArmWorldY(double structuralHeight) {
        return structuralHeight * 0.68 - 0.28;
    }
}
