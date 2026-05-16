package com.raccoon.cloud.drone.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.dto.RoutePlanCreateRequest;
import com.raccoon.cloud.drone.entity.UavRoutePlan;
import com.raccoon.cloud.drone.mapper.UavRoutePlanMapper;
import com.raccoon.cloud.drone.service.UavRoutePlanService;
import com.raccoon.cloud.drone.util.GeoPathUtils;
import com.raccoon.cloud.drone.util.PointStringParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UavRoutePlanServiceImpl extends ServiceImpl<UavRoutePlanMapper, UavRoutePlan>
        implements UavRoutePlanService {

    /** 巡航速度（m/s），用于估算耗时 */
    private static final double CRUISE_SPEED_MPS = 8.0;
    /** 粗略电量模型：每公里消耗电量（%） */
    private static final double BATTERY_PCT_PER_KM = 12.0;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UavRoutePlan planAndSave(RoutePlanCreateRequest request) {
        GeoPoint start = PointStringParser.parseLngLatHeight(request.getStartPoint());
        GeoPoint end = PointStringParser.parseLngLatHeight(request.getEndPoint());

        List<GeoPoint> path;
        if (request.getPathPoints() != null && !request.getPathPoints().isEmpty()) {
            path = request.getPathPoints();
        } else {
            path = List.of(start, end);
        }

        double distanceM = GeoPathUtils.totalPathLengthMeters(path);
        int estSeconds = (int) Math.ceil(distanceM / CRUISE_SPEED_MPS);
        float estBattery = (float) Math.min(100.0, (distanceM / 1000.0) * BATTERY_PCT_PER_KM);

        String algorithm = request.getAlgorithm().trim();
        if (algorithm.length() > 20) {
            algorithm = algorithm.substring(0, 20);
        }

        UavRoutePlan row = new UavRoutePlan();
        row.setTaskId(request.getTaskId());
        row.setMapId(request.getMapId());
        row.setUavId(request.getUavId());
        row.setStartPoint(request.getStartPoint());
        row.setEndPoint(request.getEndPoint());
        row.setTotalDistance(distanceM);
        row.setEstimatedTime(estSeconds);
        row.setEstimatedBattery(estBattery);
        row.setAlgorithm(algorithm);
        try {
            row.setPathPoints(objectMapper.writeValueAsString(path));
            row.setPhotoPoints(objectMapper.writeValueAsString(
                    request.getPhotoPoints() == null ? Collections.emptyList() : request.getPhotoPoints()));
            row.setVisitOrder(objectMapper.writeValueAsString(
                    request.getVisitOrder() == null ? Collections.emptyList() : request.getVisitOrder()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }

        save(row);
        return row;
    }
}
