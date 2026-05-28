package com.raccoon.cloud.drone.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.drone.dto.InspectionRobotSaveRequest;
import com.raccoon.cloud.drone.dto.InspectionRobotVO;
import com.raccoon.cloud.drone.dto.InspectionSceneVO;
import com.raccoon.cloud.drone.entity.UavInfo;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.enums.InspectionRobotTypeEnum;
import com.raccoon.cloud.drone.mapper.UavInfoMapper;
import com.raccoon.cloud.drone.mapper.UavMapMapper;
import com.raccoon.cloud.drone.service.InspectionRobotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionRobotServiceImpl implements InspectionRobotService {

    private final UavInfoMapper uavInfoMapper;
    private final UavMapMapper uavMapMapper;

    @Override
    public Page<InspectionRobotVO> page(long current, long size, Long mapId, String robotType, String keyword) {
        LambdaQueryWrapper<UavInfo> w = buildQuery(mapId, robotType, keyword);
        Page<UavInfo> raw = uavInfoMapper.selectPage(new Page<>(current, size), w);
        Map<Long, UavMap> mapCache = loadMapCache();
        Page<InspectionRobotVO> vo = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        vo.setRecords(raw.getRecords().stream().map(r -> toVo(r, mapCache)).toList());
        return vo;
    }

    @Override
    public List<InspectionSceneVO> listScenesWithRobots() {
        List<UavMap> maps = uavMapMapper.selectList(
                new LambdaQueryWrapper<UavMap>().orderByAsc(UavMap::getId));
        List<UavInfo> robots = uavInfoMapper.selectList(
                new LambdaQueryWrapper<UavInfo>().orderByAsc(UavInfo::getMapId, UavInfo::getId));
        Map<Long, List<UavInfo>> byMap = robots.stream()
                .filter(r -> r.getMapId() != null)
                .collect(Collectors.groupingBy(UavInfo::getMapId));

        List<InspectionSceneVO> scenes = new ArrayList<>();
        for (UavMap m : maps) {
            InspectionSceneVO s = new InspectionSceneVO();
            s.setId(m.getId());
            s.setMapName(m.getMapName());
            s.setSceneType(m.getSceneType());
            s.setRemark(m.getRemark());
            List<UavInfo> list = byMap.getOrDefault(m.getId(), List.of());
            s.setRobotCount(list.size());
            s.setRobots(list.stream().map(r -> toVo(r, Map.of(m.getId(), m))).toList());
            scenes.add(s);
        }
        return scenes;
    }

    @Override
    public List<InspectionRobotVO> listByMapId(Long mapId) {
        if (mapId == null) {
            return List.of();
        }
        List<UavInfo> list = uavInfoMapper.selectList(
                new LambdaQueryWrapper<UavInfo>()
                        .eq(UavInfo::getMapId, mapId)
                        .orderByAsc(UavInfo::getId));
        Map<Long, UavMap> mapCache = loadMapCache();
        return list.stream().map(r -> toVo(r, mapCache)).toList();
    }

    @Override
    public List<InspectionRobotVO> listBySceneType(String sceneType) {
        if (!StringUtils.hasText(sceneType)) {
            return List.of();
        }
        List<UavMap> maps = uavMapMapper.selectList(
                new LambdaQueryWrapper<UavMap>().eq(UavMap::getSceneType, sceneType.trim()));
        if (maps.isEmpty()) {
            return List.of();
        }
        List<Long> mapIds = maps.stream().map(UavMap::getId).toList();
        List<UavInfo> list = uavInfoMapper.selectList(
                new LambdaQueryWrapper<UavInfo>()
                        .in(UavInfo::getMapId, mapIds)
                        .eq(UavInfo::getStatus, 1)
                        .orderByAsc(UavInfo::getId));
        Map<Long, UavMap> mapCache = maps.stream().collect(Collectors.toMap(UavMap::getId, m -> m));
        return list.stream().map(r -> toVo(r, mapCache)).toList();
    }

    @Override
    public InspectionRobotVO getById(Long id) {
        UavInfo row = uavInfoMapper.selectById(id);
        if (row == null) {
            throw new IllegalArgumentException("巡检机器人不存在");
        }
        return toVo(row, loadMapCache());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InspectionRobotVO save(InspectionRobotSaveRequest request) {
        validateMarkerUnique(request.getMapId(), request.getMarkerLabel(), request.getId());

        UavInfo row = new UavInfo();
        if (request.getId() != null) {
            row = uavInfoMapper.selectById(request.getId());
            if (row == null) {
                throw new IllegalArgumentException("巡检机器人不存在");
            }
        }
        row.setUavName(request.getUavName().trim());
        row.setUavCode(StringUtils.hasText(request.getUavCode()) ? request.getUavCode().trim() : null);
        row.setRobotType(request.getRobotType().trim());
        row.setMapId(request.getMapId());
        row.setMarkerLabel(request.getMarkerLabel().trim());
        row.setMarkerColor(StringUtils.hasText(request.getMarkerColor()) ? request.getMarkerColor().trim() : "#409EFF");
        row.setSceneX(request.getSceneX());
        row.setSceneY(request.getSceneY());
        row.setSceneZ(request.getSceneZ());
        row.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        row.setRemark(request.getRemark());

        if (request.getId() == null) {
            uavInfoMapper.insert(row);
        } else {
            uavInfoMapper.updateById(row);
        }
        return getById(row.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (uavInfoMapper.selectById(id) == null) {
            throw new IllegalArgumentException("巡检机器人不存在");
        }
        uavInfoMapper.deleteById(id);
    }

    private void validateMarkerUnique(Long mapId, String markerLabel, Long excludeId) {
        LambdaQueryWrapper<UavInfo> w = new LambdaQueryWrapper<UavInfo>()
                .eq(UavInfo::getMapId, mapId)
                .eq(UavInfo::getMarkerLabel, markerLabel.trim());
        if (excludeId != null) {
            w.ne(UavInfo::getId, excludeId);
        }
        if (uavInfoMapper.selectCount(w) > 0) {
            throw new IllegalArgumentException("该场景下标记「" + markerLabel + "」已存在，请更换");
        }
    }

    private LambdaQueryWrapper<UavInfo> buildQuery(Long mapId, String robotType, String keyword) {
        LambdaQueryWrapper<UavInfo> w = new LambdaQueryWrapper<>();
        w.eq(mapId != null, UavInfo::getMapId, mapId);
        w.eq(StringUtils.hasText(robotType), UavInfo::getRobotType, robotType);
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(UavInfo::getUavName, k)
                    .or().like(UavInfo::getUavCode, k)
                    .or().like(UavInfo::getMarkerLabel, k));
        }
        w.orderByAsc(UavInfo::getMapId, UavInfo::getId);
        return w;
    }

    private Map<Long, UavMap> loadMapCache() {
        return uavMapMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(UavMap::getId, m -> m));
    }

    private InspectionRobotVO toVo(UavInfo r, Map<Long, UavMap> mapCache) {
        InspectionRobotVO vo = new InspectionRobotVO();
        vo.setId(r.getId());
        vo.setUavName(r.getUavName());
        vo.setUavCode(r.getUavCode());
        vo.setRobotType(r.getRobotType());
        vo.setRobotTypeLabel(InspectionRobotTypeEnum.labelOf(r.getRobotType()));
        vo.setMapId(r.getMapId());
        vo.setMarkerLabel(r.getMarkerLabel());
        vo.setMarkerColor(r.getMarkerColor());
        vo.setSceneX(r.getSceneX());
        vo.setSceneY(r.getSceneY());
        vo.setSceneZ(r.getSceneZ());
        vo.setStatus(r.getStatus());
        vo.setRemark(r.getRemark());
        if (r.getMapId() != null && mapCache.containsKey(r.getMapId())) {
            UavMap m = mapCache.get(r.getMapId());
            vo.setMapName(m.getMapName());
            vo.setSceneType(m.getSceneType());
        }
        return vo;
    }
}
