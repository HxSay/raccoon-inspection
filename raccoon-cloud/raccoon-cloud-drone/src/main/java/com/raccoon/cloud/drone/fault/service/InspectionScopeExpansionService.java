package com.raccoon.cloud.drone.fault.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.raccoon.cloud.drone.dispatch.knowledge.AgentKnowledgeClient;
import com.raccoon.cloud.drone.dispatch.model.knowledge.TopologyNode;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.fault.dto.ExpandedScope;
import com.raccoon.cloud.drone.fault.dto.FaultResponsePlan;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import com.raccoon.cloud.drone.llm.catalog.InspectionCatalogService;
import com.raccoon.cloud.drone.llm.catalog.PatrolSceneGeometry;
import com.raccoon.cloud.drone.mapper.UavInspectionDeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InspectionScopeExpansionService {

    private static final Pattern PATROL_TOWER_NAME = Pattern.compile("杆塔\\s*([1-5])");

    private final AgentKnowledgeClient agentKnowledgeClient;
    private final UavInspectionDeviceMapper deviceMapper;
    private final InspectionCatalogService catalogService;

    public ExpandedScope calculate(FaultEvent event, FaultResponsePlan plan) {
        ExpandedScope scope = new ExpandedScope();
        scope.setPrimaryDeviceId(event.getDeviceId());
        if (plan.isExpandScope() && event.getDeviceId() != null) {
            List<Long> related = queryRelatedDevices(event.getDeviceId(), plan.getExpandDepth());
            scope.setRelatedDeviceIds(related);
        }
        if (plan.getExpandAreaRadiusM() != null && event.getDeviceId() != null) {
            UavInspectionDevice primary = deviceMapper.selectById(event.getDeviceId());
            if (primary != null && primary.getLongitude() != null && primary.getLatitude() != null) {
                scope.setExpandCenterLon(primary.getLongitude());
                scope.setExpandCenterLat(primary.getLatitude());
                scope.setExpandRadiusM(plan.getExpandAreaRadiusM());
                List<Long> inRadius = findDevicesInRadius(event.getMapId(),
                        primary.getLongitude(), primary.getLatitude(), plan.getExpandAreaRadiusM());
                Set<Long> merged = new LinkedHashSet<>(scope.getAllDeviceIds());
                merged.addAll(inRadius);
                scope.setRelatedDeviceIds(new ArrayList<>(merged));
            }
        }
        applyCriticalFirePatrolExpansion(event, plan, scope);
        scope.setCheckPoints(buildCheckPoints(scope));
        return scope;
    }

    /**
     * 紧急火情：输电场景扩至走廊全线杆塔（仿真 1~5 基），实现「扩大巡检范围」而非单点复拍。
     */
    private void applyCriticalFirePatrolExpansion(FaultEvent event, FaultResponsePlan plan, ExpandedScope scope) {
        if (plan.getLevel() != FaultLevel.CRITICAL || event.getFaultType() == null) {
            return;
        }
        String ft = event.getFaultType().trim().toUpperCase();
        if (!ft.contains("FIRE") && !ft.contains("SMOKE")) {
            return;
        }
        Long mapId = event.getMapId() != null ? event.getMapId() : 1L;
        Set<Long> merged = new LinkedHashSet<>(scope.getAllDeviceIds());
        for (UavInspectionDevice d : catalogService.listDevicesByMap(mapId)) {
            if (d.getDeviceName() == null) {
                continue;
            }
            Matcher m = PATROL_TOWER_NAME.matcher(d.getDeviceName().trim());
            if (m.find()) {
                merged.add(d.getId());
            }
        }
        if (merged.size() <= 1) {
            for (int t = 1; t <= 5; t++) {
                catalogService.findDevicesByMapAndNames(mapId, List.of("杆塔" + t))
                        .forEach(dev -> merged.add(dev.getId()));
            }
        }
        scope.setRelatedDeviceIds(merged.stream()
                .filter(id -> !id.equals(scope.getPrimaryDeviceId()))
                .toList());
    }

    private List<Long> queryRelatedDevices(Long deviceId, int depth) {
        int d = Math.max(1, Math.min(depth, 3));
        List<TopologyNode> nodes = agentKnowledgeClient.queryTopology(List.of(deviceId));
        Set<Long> ids = new LinkedHashSet<>();
        for (TopologyNode n : nodes) {
            if (n.getTargetId() != null && !n.getTargetId().equals(deviceId)) {
                ids.add(n.getTargetId());
            }
        }
        if (ids.isEmpty() && d > 0) {
            UavInspectionDevice anchor = deviceMapper.selectById(deviceId);
            if (anchor != null && anchor.getMapId() != null) {
                LambdaQueryWrapper<UavInspectionDevice> q = new LambdaQueryWrapper<>();
                q.eq(UavInspectionDevice::getMapId, anchor.getMapId());
                q.ne(UavInspectionDevice::getId, deviceId);
                q.last("LIMIT " + (d * 5));
                deviceMapper.selectList(q).forEach(dev -> ids.add(dev.getId()));
            }
        }
        return new ArrayList<>(ids);
    }

    private List<Long> findDevicesInRadius(Long mapId, double lon, double lat, double radiusM) {
        if (mapId == null) {
            return List.of();
        }
        List<UavInspectionDevice> all = deviceMapper.selectList(
                new LambdaQueryWrapper<UavInspectionDevice>().eq(UavInspectionDevice::getMapId, mapId));
        List<Long> result = new ArrayList<>();
        for (UavInspectionDevice dev : all) {
            if (dev.getLongitude() == null || dev.getLatitude() == null) {
                continue;
            }
            double dist = haversineM(lon, lat, dev.getLongitude(), dev.getLatitude());
            if (dist <= radiusM) {
                result.add(dev.getId());
            }
        }
        return result;
    }

    private List<GeoPoint> buildCheckPoints(ExpandedScope scope) {
        List<GeoPoint> points = new ArrayList<>();
        for (Long id : scope.getAllDeviceIds()) {
            UavInspectionDevice dev = deviceMapper.selectById(id);
            if (dev == null) {
                continue;
            }
            GeoPoint p = geoFromDevice(dev);
            if (p != null) {
                points.add(p);
            }
        }
        points.sort(Comparator.comparing(GeoPoint::getLongitude, Comparator.nullsLast(Double::compareTo)));
        return points;
    }

    private GeoPoint geoFromDevice(UavInspectionDevice dev) {
        if (dev.getLongitude() != null && dev.getLatitude() != null) {
            GeoPoint p = new GeoPoint();
            p.setLongitude(dev.getLongitude());
            p.setLatitude(dev.getLatitude());
            p.setHeight(dev.getHeight() != null ? dev.getHeight() : 0.0);
            return p;
        }
        if (dev.getDeviceName() != null) {
            Matcher m = PATROL_TOWER_NAME.matcher(dev.getDeviceName().trim());
            if (m.find()) {
                return PatrolSceneGeometry.towerPhotoPoint(Integer.parseInt(m.group(1)));
            }
        }
        return null;
    }

    private static double haversineM(double lon1, double lat1, double lon2, double lat2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
