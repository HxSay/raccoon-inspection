package com.raccoon.cloud.drone.llm.catalog;

import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按设备名称解析输电场景杆塔拍照航点（WGS84，与仿真一致）。
 */
@Component
public class PatrolDeviceWaypointResolver {

    private static final Pattern TOWER_NAME = Pattern.compile("杆塔\\s*(\\d+)");

    public boolean supportsPatrolMap(Long mapId) {
        return mapId != null && mapId == 1L;
    }

    public Optional<Integer> parseTowerIndex(String deviceName) {
        if (!StringUtils.hasText(deviceName)) {
            return Optional.empty();
        }
        Matcher m = TOWER_NAME.matcher(deviceName.trim());
        if (m.find()) {
            return Optional.of(Integer.parseInt(m.group(1)));
        }
        return Optional.empty();
    }

    /**
     * 将设备列表按杆塔序号排序，并解析为拍照地理点。
     */
    public List<ResolvedTowerVisit> resolveVisits(List<UavInspectionDevice> devices) {
        List<ResolvedTowerVisit> visits = new ArrayList<>();
        for (UavInspectionDevice d : devices) {
            parseTowerIndex(d.getDeviceName()).ifPresent(idx -> {
                GeoPoint photo = PatrolSceneGeometry.towerPhotoPoint(idx);
                visits.add(new ResolvedTowerVisit(idx, d.getId(), d.getDeviceName(), photo));
            });
        }
        visits.sort(Comparator.comparingInt(ResolvedTowerVisit::towerIndex));
        return visits;
    }

    public record ResolvedTowerVisit(int towerIndex, Long deviceId, String deviceName, GeoPoint photoPoint) {
    }
}
