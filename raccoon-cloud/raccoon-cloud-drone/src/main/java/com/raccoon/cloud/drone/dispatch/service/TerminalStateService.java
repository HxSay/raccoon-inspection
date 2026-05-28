package com.raccoon.cloud.drone.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.raccoon.cloud.drone.dispatch.cache.TerminalStateCacheStore;
import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dispatch.dto.DispatchSnapshotVO;
import com.raccoon.cloud.drone.dispatch.model.TerminalCapacity;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import com.raccoon.cloud.drone.dto.GeoPoint;
import com.raccoon.cloud.drone.entity.UavInfo;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.entity.UavRobotRuntimeStatus;
import com.raccoon.cloud.drone.mapper.UavInfoMapper;
import com.raccoon.cloud.drone.mapper.UavMapMapper;
import com.raccoon.cloud.drone.mapper.UavRobotRuntimeStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 终端实时状态感知服务（步骤 2）。
 * <p>查询路径：
 * <ol>
 *   <li>优先命中 {@link TerminalStateCacheStore} 缓存（语义对齐 Redis）</li>
 *   <li>缓存未命中则查询数据库聚合 {@code uav_info} + {@code uav_robot_runtime_status} + {@code uav_map}</li>
 *   <li>调用 {@link TerminalCapacityProfile} 构建能力模型并回写缓存</li>
 * </ol>
 *
 * @author raccoon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalStateService {

    private final UavInfoMapper uavInfoMapper;
    private final UavMapMapper uavMapMapper;
    private final UavRobotRuntimeStatusMapper runtimeMapper;
    private final TerminalCapacityProfile capacityProfile;
    private final TerminalStateCacheStore cacheStore;

    /**
     * 获取单个终端的实时状态。
     *
     * @param terminalId 终端 ID
     * @return 状态对象；终端不存在时返回 {@link Optional#empty()}
     */
    public Optional<TerminalState> getTerminalState(Long terminalId) {
        if (terminalId == null) {
            return Optional.empty();
        }
        Optional<TerminalState> cached = cacheStore.get(terminalId);
        if (cached.isPresent()) {
            log.debug("[terminal-state] cache HIT terminalId={}", terminalId);
            return cached;
        }
        UavInfo uav = uavInfoMapper.selectById(terminalId);
        if (uav == null) {
            log.warn("[terminal-state] terminalId={} 不存在", terminalId);
            return Optional.empty();
        }
        UavMap map = uav.getMapId() == null ? null : uavMapMapper.selectById(uav.getMapId());
        UavRobotRuntimeStatus runtime = runtimeMapper.selectById(terminalId);
        TerminalState state = aggregate(uav, map, runtime);
        cacheStore.put(state);
        return Optional.of(state);
    }

    /**
     * 列出某场景下全部终端（含离线，但会标记 online 状态）。
     *
     * @param mapId 场景地图 ID（非空）
     * @return 终端状态列表
     */
    public List<TerminalState> listByMap(Long mapId) {
        if (mapId == null) {
            return List.of();
        }
        List<UavInfo> uavs = uavInfoMapper.selectList(
                new LambdaQueryWrapper<UavInfo>()
                        .eq(UavInfo::getMapId, mapId)
                        .orderByAsc(UavInfo::getId));
        if (uavs.isEmpty()) {
            return List.of();
        }
        if (uavs.size() > DispatchConstants.MAX_TERMINALS_PER_SCENE) {
            log.warn("[terminal-state] 场景 {} 终端数 {} 超过上限 {}，仅返回前 {} 个",
                    mapId, uavs.size(), DispatchConstants.MAX_TERMINALS_PER_SCENE,
                    DispatchConstants.MAX_TERMINALS_PER_SCENE);
            uavs = uavs.subList(0, DispatchConstants.MAX_TERMINALS_PER_SCENE);
        }
        UavMap map = uavMapMapper.selectById(mapId);
        List<Long> ids = uavs.stream().map(UavInfo::getId).toList();
        Map<Long, UavRobotRuntimeStatus> runtimeMap = loadRuntimeMap(ids);

        List<TerminalState> result = new ArrayList<>(uavs.size());
        for (UavInfo uav : uavs) {
            Optional<TerminalState> cached = cacheStore.get(uav.getId());
            if (cached.isPresent()) {
                result.add(cached.get());
                continue;
            }
            TerminalState state = aggregate(uav, map, runtimeMap.get(uav.getId()));
            cacheStore.put(state);
            result.add(state);
        }
        return result;
    }

    /**
     * 按场景汇总成调度快照，供中央 Agent 拉取。
     *
     * @param mapId 场景 ID
     * @return 快照 VO
     */
    public DispatchSnapshotVO buildSnapshot(Long mapId) {
        DispatchSnapshotVO vo = new DispatchSnapshotVO();
        vo.setMapId(mapId);
        vo.setSnapshotAt(LocalDateTime.now());
        if (mapId == null) {
            return vo;
        }
        UavMap map = uavMapMapper.selectById(mapId);
        if (map != null) {
            vo.setMapName(map.getMapName());
            vo.setSceneType(map.getSceneType());
        }
        List<TerminalState> states = listByMap(mapId);
        vo.setTerminals(states);
        vo.setTotalTerminals(states.size());
        vo.setOnlineTerminals((int) states.stream().filter(TerminalState::isOnline).count());
        vo.setAvailableTerminals((int) states.stream()
                .filter(s -> s.canAcceptTask(DispatchConstants.MIN_BATTERY_PCT,
                        DispatchConstants.MAX_TASK_PER_TERMINAL))
                .count());
        return vo;
    }

    /**
     * 主动失效缓存（终端上下线、配置变更时调用）。
     *
     * @param terminalId 终端 ID
     */
    public void evict(Long terminalId) {
        cacheStore.evict(terminalId);
    }

    /** 批量加载 runtime 表，避免 N+1。 */
    private Map<Long, UavRobotRuntimeStatus> loadRuntimeMap(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return new HashMap<>();
        }
        List<UavRobotRuntimeStatus> list = runtimeMapper.selectList(
                new LambdaQueryWrapper<UavRobotRuntimeStatus>()
                        .in(UavRobotRuntimeStatus::getUavId, ids));
        return list.stream().collect(Collectors.toMap(UavRobotRuntimeStatus::getUavId, r -> r));
    }

    /** 把 entity → 聚合视图。 */
    private TerminalState aggregate(UavInfo uav, UavMap map, UavRobotRuntimeStatus runtime) {
        TerminalState state = new TerminalState();
        state.setTerminalId(uav.getId());
        state.setTerminalCode(uav.getUavCode());
        state.setTerminalName(uav.getUavName());
        state.setTerminalType(uav.getRobotType());
        state.setMapId(uav.getMapId());
        state.setWorkRangeDesc(uav.getWorkRangeDesc());
        if (map != null) {
            state.setSceneType(map.getSceneType());
        }

        if (runtime != null) {
            if (runtime.getLongitude() != null && runtime.getLatitude() != null) {
                state.setPosition(new GeoPoint(runtime.getLongitude(), runtime.getLatitude(),
                        runtime.getHeight() == null ? 0.0 : runtime.getHeight()));
            }
            state.setPositionAt(runtime.getPositionAt());
            state.setBatteryPct(runtime.getBatteryPct());
            state.setEnduranceMin(runtime.getEnduranceMin());
            state.setAssignedTaskCount(runtime.getAssignedTaskCount());
            state.setCpuPct(runtime.getCpuPct());
            state.setMemoryPct(runtime.getMemoryPct());
            state.setFlightStatus(StringUtils.hasText(runtime.getFlightStatus())
                    ? runtime.getFlightStatus() : "STANDBY");
            state.setFaultStatus(StringUtils.hasText(runtime.getFaultStatus())
                    ? runtime.getFaultStatus() : "NONE");
            state.setFaultMessage(runtime.getFaultMessage());
            state.setOnline(isOnline(runtime));
        } else {
            state.setOnline(false);
            state.setFlightStatus("OFFLINE");
            state.setFaultStatus("NONE");
            state.setAssignedTaskCount(0);
        }

        TerminalCapacity capacity = capacityProfile.build(uav, runtime);
        state.setCapacity(capacity);
        state.setAggregatedAt(LocalDateTime.now());
        return state;
    }

    /** 心跳超时判定与 InspectionRobotRuntimeServiceImpl 保持一致（15s）。 */
    private boolean isOnline(UavRobotRuntimeStatus runtime) {
        if (runtime == null) {
            return false;
        }
        boolean online = runtime.getOnlineFlag() != null && runtime.getOnlineFlag() == 1;
        LocalDateTime ref = runtime.getRuntimeAt() != null ? runtime.getRuntimeAt() : runtime.getPositionAt();
        if (ref == null) {
            return false;
        }
        if (online) {
            return !ref.isBefore(LocalDateTime.now().minusSeconds(15));
        }
        return false;
    }
}
