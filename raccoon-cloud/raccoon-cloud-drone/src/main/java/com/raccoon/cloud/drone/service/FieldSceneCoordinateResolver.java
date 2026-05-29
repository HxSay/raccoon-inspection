package com.raccoon.cloud.drone.service;

import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.llm.catalog.PatrolDeviceWaypointResolver;
import com.raccoon.cloud.drone.llm.catalog.PatrolSceneGeometry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将现场设备与 raccoon-drone-sim 输电场景几何对齐，补全 WGS84 / 场景坐标。
 */
@Component
@RequiredArgsConstructor
public class FieldSceneCoordinateResolver {

    private static final Pattern TOWER_NAME = Pattern.compile("(?:杆塔|塔杆|塔)(\\d+)");

    private final PatrolDeviceWaypointResolver patrolWaypointResolver;

    /**
     * 若坐标或场景对象 ID 缺失，则按场景类型与设备类型写入默认值。
     *
     * @return 是否写入了新坐标
     */
    public boolean fillIfMissing(UavInspectionDevice row, UavMap map) {
        if (row == null || map == null) {
            return false;
        }
        boolean changed = false;
        if (!StringUtils.hasText(row.getSceneObjectId())) {
            row.setSceneObjectId(generateSceneObjectId(row));
            changed = true;
        }
        if (!StringUtils.hasText(row.getSceneType()) && StringUtils.hasText(map.getSceneType())) {
            row.setSceneType(map.getSceneType());
            changed = true;
        }
        if (!StringUtils.hasText(row.getLocationDesc())) {
            row.setLocationDesc(map.getMapName() + " · " + row.getDeviceName());
            changed = true;
        }
        if (hasSceneCoords(row) && hasGeoCoords(row)) {
            return changed;
        }
        if ("patrol".equalsIgnoreCase(map.getSceneType())) {
            changed |= fillPatrol(row, map);
        }
        return changed;
    }

    private boolean fillPatrol(UavInspectionDevice row, UavMap map) {
        String type = row.getDeviceType() == null ? "" : row.getDeviceType().toUpperCase();
        String name = row.getDeviceName() == null ? "" : row.getDeviceName();

        GeoPoint geo = null;
        double sceneX = 0;
        double sceneY = 4;
        double sceneZ = PatrolSceneGeometry.CORRIDOR_Z0;

        if (type.contains("TOWER") || "tower".equalsIgnoreCase(row.getDeviceType()) || name.contains("杆塔")) {
            int idx = parseTowerIndex(name).orElse(1);
            geo = PatrolSceneGeometry.towerCenterPoint(idx);
            sceneX = PatrolSceneGeometry.towerSceneX(idx);
            sceneY = 4;
            sceneZ = PatrolSceneGeometry.CORRIDOR_Z0;
            if (!StringUtils.hasText(row.getSceneObjectId()) || row.getSceneObjectId().startsWith("cloud-")) {
                row.setSceneObjectId("builtin-tower-" + idx);
            }
        } else if (type.contains("TRANSFORMER") || name.contains("变压器")) {
            geo = PatrolSceneGeometry.patrolTransformerPoint();
            sceneX = -95;
            sceneY = 4;
            sceneZ = 52;
        } else if (type.contains("BREAKER") || name.contains("断路器")) {
            geo = PatrolSceneGeometry.patrolBreakerPoint();
            sceneX = -68;
            sceneY = 3;
            sceneZ = 58;
        } else {
            geo = PatrolSceneGeometry.nestTakeoff();
            sceneX = PatrolSceneGeometry.NEST_X + 30;
            sceneY = PatrolSceneGeometry.NEST_Y;
            sceneZ = PatrolSceneGeometry.NEST_Z + 15;
        }

        boolean changed = false;
        if (!hasGeoCoords(row) && geo != null) {
            row.setLongitude(geo.getLongitude());
            row.setLatitude(geo.getLatitude());
            row.setHeight(geo.getHeight());
            changed = true;
        }
        if (!hasSceneCoords(row)) {
            row.setSceneX(sceneX);
            row.setSceneY(sceneY);
            row.setSceneZ(sceneZ);
            changed = true;
        }
        if (!StringUtils.hasText(row.getLocationDesc())) {
            row.setLocationDesc(map.getMapName() + " · " + row.getDeviceName());
            changed = true;
        }
        return changed;
    }

    private java.util.Optional<Integer> parseTowerIndex(String name) {
        Matcher m = TOWER_NAME.matcher(name);
        if (m.find()) {
            return java.util.Optional.of(Integer.parseInt(m.group(1)));
        }
        return patrolWaypointResolver.parseTowerIndex(name);
    }

    private String generateSceneObjectId(UavInspectionDevice row) {
        if (row.getId() != null) {
            return "cloud-" + row.getId();
        }
        return "cloud-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean hasSceneCoords(UavInspectionDevice row) {
        return row.getSceneX() != null && row.getSceneY() != null && row.getSceneZ() != null;
    }

    private boolean hasGeoCoords(UavInspectionDevice row) {
        return row.getLongitude() != null && row.getLatitude() != null && row.getHeight() != null;
    }
}
