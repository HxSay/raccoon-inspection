package com.raccoon.cloud.drone.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceSaveRequest;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceSyncItem;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceSyncRequest;
import com.raccoon.cloud.drone.dto.FieldSceneDeviceVO;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.enums.FieldSceneDeviceTypeEnum;
import com.raccoon.cloud.drone.llm.catalog.PatrolSceneGeometry;
import com.raccoon.cloud.drone.mapper.UavInspectionDeviceMapper;
import com.raccoon.cloud.drone.mapper.UavMapMapper;
import com.raccoon.cloud.drone.service.FieldSceneDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FieldSceneDeviceServiceImpl implements FieldSceneDeviceService {

    private static final String SYNC_SCENE = "SCENE_SYNC";
    private static final String SYNC_BUILTIN = "BUILTIN";
    private static final String SYNC_MANUAL = "MANUAL";

    private final UavInspectionDeviceMapper deviceMapper;
    private final UavMapMapper mapMapper;

    @Override
    public Page<FieldSceneDeviceVO> page(long current, long size, Long mapId, String sceneType, String keyword) {
        LambdaQueryWrapper<UavInspectionDevice> w = buildQuery(mapId, sceneType, keyword);
        Page<UavInspectionDevice> raw = deviceMapper.selectPage(new Page<>(current, size), w);
        Map<Long, UavMap> maps = loadMapCache();
        Page<FieldSceneDeviceVO> vo = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        vo.setRecords(raw.getRecords().stream().map(r -> toVo(r, maps)).toList());
        return vo;
    }

    @Override
    public List<FieldSceneDeviceVO> listByMapId(Long mapId) {
        if (mapId == null) {
            return List.of();
        }
        List<UavInspectionDevice> list = deviceMapper.selectList(
                new LambdaQueryWrapper<UavInspectionDevice>()
                        .eq(UavInspectionDevice::getMapId, mapId)
                        .orderByAsc(UavInspectionDevice::getId));
        Map<Long, UavMap> maps = loadMapCache();
        return list.stream().map(r -> toVo(r, maps)).toList();
    }

    @Override
    public FieldSceneDeviceVO getById(Long id) {
        UavInspectionDevice row = deviceMapper.selectById(id);
        if (row == null) {
            throw new IllegalArgumentException("现场设备不存在");
        }
        return toVo(row, loadMapCache());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldSceneDeviceVO save(FieldSceneDeviceSaveRequest request) {
        UavMap map = mapMapper.selectById(request.getMapId());
        if (map == null) {
            throw new IllegalArgumentException("虚拟场景不存在");
        }
        UavInspectionDevice row;
        if (request.getId() != null) {
            row = deviceMapper.selectById(request.getId());
            if (row == null) {
                throw new IllegalArgumentException("现场设备不存在");
            }
        } else {
            row = new UavInspectionDevice();
            row.setSyncSource(StringUtils.hasText(request.getSyncSource()) ? request.getSyncSource() : SYNC_MANUAL);
        }
        applySave(row, request, map);
        if (request.getId() == null) {
            deviceMapper.insert(row);
        } else {
            deviceMapper.updateById(row);
        }
        return getById(row.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UavInspectionDevice row = deviceMapper.selectById(id);
        if (row == null) {
            throw new IllegalArgumentException("现场设备不存在");
        }
        if (SYNC_BUILTIN.equals(row.getSyncSource())) {
            throw new IllegalArgumentException("内置杆塔不可删除，可改为停用");
        }
        deviceMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncFromScene(FieldSceneDeviceSyncRequest request) {
        UavMap map = mapMapper.selectById(request.getMapId());
        if (map == null) {
            throw new IllegalArgumentException("虚拟场景不存在");
        }
        String sceneType = StringUtils.hasText(request.getSceneType())
                ? request.getSceneType().trim() : map.getSceneType();

        Set<String> incomingIds = new HashSet<>();
        int upserted = 0;
        for (FieldSceneDeviceSyncItem item : request.getDevices()) {
            incomingIds.add(item.getSceneObjectId());
            UavInspectionDevice existing = deviceMapper.selectOne(
                    new LambdaQueryWrapper<UavInspectionDevice>()
                            .eq(UavInspectionDevice::getMapId, request.getMapId())
                            .eq(UavInspectionDevice::getSceneObjectId, item.getSceneObjectId())
                            .last("LIMIT 1"));
            if (existing == null) {
                existing = new UavInspectionDevice();
                existing.setMapId(request.getMapId());
                existing.setSceneObjectId(item.getSceneObjectId());
                existing.setSyncSource(SYNC_SCENE);
                existing.setStatus(1);
            } else if (SYNC_BUILTIN.equals(existing.getSyncSource())) {
                continue;
            } else {
                existing.setSyncSource(SYNC_SCENE);
            }
            existing.setDeviceName(item.getDeviceName().trim());
            existing.setDeviceType(StringUtils.hasText(item.getDeviceType()) ? item.getDeviceType() : "CUSTOM");
            existing.setLongitude(item.getLongitude());
            existing.setLatitude(item.getLatitude());
            existing.setHeight(item.getHeight());
            existing.setSceneX(item.getSceneX());
            existing.setSceneY(item.getSceneY());
            existing.setSceneZ(item.getSceneZ());
            existing.setSceneType(sceneType);
            existing.setLocationDesc(item.getLocationDesc());
            existing.setRemark(item.getRemark());
            existing.setStatus(1);

            if (existing.getId() == null) {
                deviceMapper.insert(existing);
            } else {
                deviceMapper.updateById(existing);
            }
            upserted++;
        }

        List<UavInspectionDevice> sceneSynced = deviceMapper.selectList(
                new LambdaQueryWrapper<UavInspectionDevice>()
                        .eq(UavInspectionDevice::getMapId, request.getMapId())
                        .eq(UavInspectionDevice::getSyncSource, SYNC_SCENE));
        for (UavInspectionDevice d : sceneSynced) {
            if (d.getSceneObjectId() != null && !incomingIds.contains(d.getSceneObjectId())) {
                d.setStatus(0);
                deviceMapper.updateById(d);
            }
        }
        return upserted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int initBuiltinPatrolTowers() {
        UavMap map = mapMapper.selectOne(
                new LambdaQueryWrapper<UavMap>().eq(UavMap::getSceneType, "patrol").last("LIMIT 1"));
        if (map == null) {
            map = mapMapper.selectById(1L);
        }
        if (map == null) {
            return 0;
        }
        int count = 0;
        for (int tower = 1; tower <= 5; tower++) {
            String name = "杆塔" + tower;
            GeoPoint photo = PatrolSceneGeometry.towerPhotoPoint(tower);
            String objectId = "builtin-tower-" + tower;

            UavInspectionDevice row = deviceMapper.selectOne(
                    new LambdaQueryWrapper<UavInspectionDevice>()
                            .eq(UavInspectionDevice::getMapId, map.getId())
                            .eq(UavInspectionDevice::getDeviceName, name)
                            .last("LIMIT 1"));
            if (row == null) {
                row = new UavInspectionDevice();
                row.setMapId(map.getId());
                row.setDeviceName(name);
                row.setStatus(1);
            }
            row.setDeviceType("TOWER");
            row.setSyncSource(SYNC_BUILTIN);
            row.setSceneObjectId(objectId);
            row.setSceneType("patrol");
            row.setLongitude(photo.getLongitude());
            row.setLatitude(photo.getLatitude());
            row.setHeight(photo.getHeight());
            row.setSceneX(PatrolSceneGeometry.towerSceneX(tower));
            row.setSceneY(photo.getHeight());
            row.setSceneZ(PatrolSceneGeometry.CORRIDOR_Z0 + PatrolSceneGeometry.PHOTO_Z_OFFSET);
            row.setLocationDesc(map.getMapName() + " · " + name);
            row.setRemark("内置杆塔（与仿真场景对齐）");

            if (row.getId() == null) {
                deviceMapper.insert(row);
            } else {
                deviceMapper.updateById(row);
            }
            count++;
        }
        return count;
    }

    private void applySave(UavInspectionDevice row, FieldSceneDeviceSaveRequest req, UavMap map) {
        row.setMapId(req.getMapId());
        row.setDeviceName(req.getDeviceName().trim());
        row.setDeviceType(req.getDeviceType().trim());
        row.setLongitude(req.getLongitude());
        row.setLatitude(req.getLatitude());
        row.setHeight(req.getHeight());
        row.setSceneX(req.getSceneX());
        row.setSceneY(req.getSceneY());
        row.setSceneZ(req.getSceneZ());
        row.setSceneType(StringUtils.hasText(req.getSceneType()) ? req.getSceneType() : map.getSceneType());
        row.setSceneObjectId(req.getSceneObjectId());
        row.setLocationDesc(req.getLocationDesc());
        row.setRemark(req.getRemark());
        row.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        if (!StringUtils.hasText(row.getSyncSource())) {
            row.setSyncSource(SYNC_MANUAL);
        }
    }

    private LambdaQueryWrapper<UavInspectionDevice> buildQuery(Long mapId, String sceneType, String keyword) {
        LambdaQueryWrapper<UavInspectionDevice> w = new LambdaQueryWrapper<>();
        w.eq(mapId != null, UavInspectionDevice::getMapId, mapId);
        w.eq(StringUtils.hasText(sceneType), UavInspectionDevice::getSceneType, sceneType);
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(UavInspectionDevice::getDeviceName, k)
                    .or().like(UavInspectionDevice::getLocationDesc, k)
                    .or().like(UavInspectionDevice::getSceneObjectId, k));
        }
        w.orderByAsc(UavInspectionDevice::getMapId, UavInspectionDevice::getId);
        return w;
    }

    private Map<Long, UavMap> loadMapCache() {
        return mapMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(UavMap::getId, m -> m));
    }

    private FieldSceneDeviceVO toVo(UavInspectionDevice r, Map<Long, UavMap> maps) {
        FieldSceneDeviceVO vo = new FieldSceneDeviceVO();
        vo.setId(r.getId());
        vo.setMapId(r.getMapId());
        vo.setDeviceName(r.getDeviceName());
        vo.setDeviceType(r.getDeviceType());
        vo.setDeviceTypeLabel(FieldSceneDeviceTypeEnum.labelOf(r.getDeviceType()));
        vo.setLongitude(r.getLongitude());
        vo.setLatitude(r.getLatitude());
        vo.setHeight(r.getHeight());
        vo.setSceneX(r.getSceneX());
        vo.setSceneY(r.getSceneY());
        vo.setSceneZ(r.getSceneZ());
        vo.setSceneType(r.getSceneType());
        vo.setSceneObjectId(r.getSceneObjectId());
        vo.setLocationDesc(r.getLocationDesc());
        vo.setSyncSource(r.getSyncSource());
        vo.setStatus(r.getStatus());
        vo.setRemark(r.getRemark());
        if (r.getMapId() != null && maps.containsKey(r.getMapId())) {
            vo.setMapName(maps.get(r.getMapId()).getMapName());
        }
        return vo;
    }
}
