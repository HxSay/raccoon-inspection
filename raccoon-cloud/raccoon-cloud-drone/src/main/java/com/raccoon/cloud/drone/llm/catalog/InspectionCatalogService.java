package com.raccoon.cloud.drone.llm.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.raccoon.cloud.drone.entity.UavInfo;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.mapper.UavInfoMapper;
import com.raccoon.cloud.drone.mapper.UavInspectionDeviceMapper;
import com.raccoon.cloud.drone.mapper.UavMapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 区域 / 设备主数据查询（槽位校验与规则降级）。
 */
@Service
@RequiredArgsConstructor
public class InspectionCatalogService {

    private final UavMapMapper uavMapMapper;
    private final UavInspectionDeviceMapper deviceMapper;
    private final UavInfoMapper uavInfoMapper;

    public Optional<UavMap> findAreaByName(String areaName) {
        if (!StringUtils.hasText(areaName)) {
            return Optional.empty();
        }
        String key = areaName.trim();
        List<UavMap> maps = uavMapMapper.selectList(new LambdaQueryWrapper<UavMap>().orderByAsc(UavMap::getId));
        for (UavMap m : maps) {
            if (m.getMapName() != null && m.getMapName().equals(key)) {
                return Optional.of(m);
            }
        }
        for (UavMap m : maps) {
            if (m.getMapName() != null && (m.getMapName().contains(key) || key.contains(m.getMapName()))) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    public List<UavInspectionDevice> findDevicesByMapAndNames(Long mapId, List<String> deviceNames) {
        List<UavInspectionDevice> result = new ArrayList<>();
        if (mapId == null || deviceNames == null) {
            return result;
        }
        for (String name : deviceNames) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String key = name.trim();
            List<UavInspectionDevice> list = deviceMapper.selectList(
                    new LambdaQueryWrapper<UavInspectionDevice>()
                            .eq(UavInspectionDevice::getMapId, mapId)
                            .eq(UavInspectionDevice::getStatus, 1));
            boolean matched = false;
            for (UavInspectionDevice d : list) {
                if (d.getDeviceName().equals(key)) {
                    result.add(d);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                for (UavInspectionDevice d : list) {
                    if (d.getDeviceName().contains(key) || key.contains(d.getDeviceName())) {
                        result.add(d);
                        matched = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    public Optional<UavInfo> findFirstUavByMap(Long mapId) {
        if (mapId == null) {
            return Optional.empty();
        }
        List<UavInfo> list = uavInfoMapper.selectList(
                new LambdaQueryWrapper<UavInfo>()
                        .eq(UavInfo::getMapId, mapId)
                        .eq(UavInfo::getStatus, 1)
                        .orderByAsc(UavInfo::getId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 统计场景内可用（status=1）无人机数量，用于机队推荐的可用机数上界。
     */
    public int countActiveUavsByMap(Long mapId) {
        if (mapId == null) {
            return 0;
        }
        Long count = uavInfoMapper.selectCount(
                new LambdaQueryWrapper<UavInfo>()
                        .eq(UavInfo::getMapId, mapId)
                        .eq(UavInfo::getStatus, 1));
        return count == null ? 0 : count.intValue();
    }

    public List<String> listAllAreaNames() {
        return uavMapMapper.selectList(new LambdaQueryWrapper<UavMap>().orderByAsc(UavMap::getId))
                .stream().map(UavMap::getMapName).filter(StringUtils::hasText).toList();
    }

    public List<UavInspectionDevice> listDevicesByMap(Long mapId) {
        if (mapId == null) {
            return List.of();
        }
        return deviceMapper.selectList(
                new LambdaQueryWrapper<UavInspectionDevice>()
                        .eq(UavInspectionDevice::getMapId, mapId)
                        .eq(UavInspectionDevice::getStatus, 1)
                        .orderByAsc(UavInspectionDevice::getId));
    }
}
