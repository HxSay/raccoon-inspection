package com.raccoon.cloud.drone.service.impl;

import com.raccoon.cloud.drone.dispatch.cache.TerminalStateCacheStore;
import com.raccoon.cloud.drone.dto.InspectionRobotRuntimeVO;
import com.raccoon.cloud.drone.dto.InspectionRobotTelemetryReport;
import com.raccoon.cloud.drone.entity.UavRobotRuntimeStatus;
import com.raccoon.cloud.drone.enums.RobotFlightStatusEnum;
import com.raccoon.cloud.drone.mapper.UavRobotRuntimeStatusMapper;
import com.raccoon.cloud.drone.service.InspectionRobotRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InspectionRobotRuntimeServiceImpl implements InspectionRobotRuntimeService {

    private static final int ONLINE_STALE_SEC = 15;

    private final UavRobotRuntimeStatusMapper runtimeMapper;
    private final TerminalStateCacheStore terminalStateCacheStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void report(InspectionRobotTelemetryReport report) {
        if (report == null || report.getUavId() == null) {
            return;
        }
        UavRobotRuntimeStatus row = runtimeMapper.selectById(report.getUavId());
        if (row == null) {
            row = new UavRobotRuntimeStatus();
            row.setUavId(report.getUavId());
            row.setFaultStatus("NONE");
            row.setAssignedTaskCount(0);
            row.setOnlineFlag(0);
        }
        LocalDateTime now = LocalDateTime.now();
        String channel = report.getChannel() == null ? "ALL" : report.getChannel().toUpperCase();

        if (channel.contains("POSITION") || channel.equals("ALL")) {
            if (report.getLongitude() != null) {
                row.setLongitude(report.getLongitude());
            }
            if (report.getLatitude() != null) {
                row.setLatitude(report.getLatitude());
            }
            if (report.getHeight() != null) {
                row.setHeight(report.getHeight());
            }
            row.setPositionAt(now);
        }
        if (channel.contains("BATTERY") || channel.equals("ALL")) {
            if (report.getBatteryPct() != null) {
                row.setBatteryPct(report.getBatteryPct());
            }
            if (report.getEnduranceMin() != null) {
                row.setEnduranceMin(report.getEnduranceMin());
            }
            row.setBatteryAt(now);
        }
        if (channel.contains("LOAD") || channel.equals("ALL")) {
            if (report.getAssignedTaskCount() != null) {
                row.setAssignedTaskCount(report.getAssignedTaskCount());
            }
            if (report.getCpuPct() != null) {
                row.setCpuPct(report.getCpuPct());
            }
            if (report.getMemoryPct() != null) {
                row.setMemoryPct(report.getMemoryPct());
            }
            row.setLoadAt(now);
        }
        if (channel.contains("RUNTIME") || channel.equals("ALL")) {
            if (report.getOnline() != null) {
                row.setOnlineFlag(Boolean.TRUE.equals(report.getOnline()) ? 1 : 0);
            }
            if (StringUtils.hasText(report.getFlightStatus())) {
                row.setFlightStatus(RobotFlightStatusEnum.normalizePhase(report.getFlightStatus()));
            }
            if (StringUtils.hasText(report.getFaultStatus())) {
                row.setFaultStatus(report.getFaultStatus());
            }
            if (report.getFaultMessage() != null) {
                row.setFaultMessage(report.getFaultMessage());
            }
            row.setRuntimeAt(now);
        }

        row.setUpdateTime(now);
        if (runtimeMapper.selectById(row.getUavId()) == null) {
            runtimeMapper.insert(row);
        } else {
            runtimeMapper.updateById(row);
        }
        terminalStateCacheStore.evict(row.getUavId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportBatch(List<InspectionRobotTelemetryReport> reports) {
        if (reports == null) {
            return;
        }
        for (InspectionRobotTelemetryReport r : reports) {
            report(r);
        }
    }

    @Override
    public InspectionRobotRuntimeVO getRuntime(Long uavId, String workRangeDesc) {
        UavRobotRuntimeStatus row = uavId == null ? null : runtimeMapper.selectById(uavId);
        return toVo(row, workRangeDesc);
    }

    @Override
    public Map<Long, InspectionRobotRuntimeVO> getRuntimeMap(List<Long> uavIds, Map<Long, String> workRangeByUav) {
        Map<Long, InspectionRobotRuntimeVO> map = new HashMap<>();
        if (uavIds == null) {
            return map;
        }
        for (Long id : uavIds) {
            UavRobotRuntimeStatus row = runtimeMapper.selectById(id);
            String wr = workRangeByUav != null ? workRangeByUav.get(id) : null;
            map.put(id, toVo(row, wr));
        }
        return map;
    }

    private InspectionRobotRuntimeVO toVo(UavRobotRuntimeStatus row, String workRangeDesc) {
        InspectionRobotRuntimeVO vo = new InspectionRobotRuntimeVO();
        vo.setWorkRangeDesc(workRangeDesc);
        if (row == null) {
            vo.setOnline(false);
            vo.setFlightStatus("OFFLINE");
            vo.setFlightStatusLabel("离线");
            vo.setFaultStatus("NONE");
            vo.setAssignedTaskCount(0);
            return vo;
        }
        vo.setLongitude(row.getLongitude());
        vo.setLatitude(row.getLatitude());
        vo.setHeight(row.getHeight());
        vo.setPositionAt(row.getPositionAt());
        vo.setBatteryPct(row.getBatteryPct());
        vo.setEnduranceMin(row.getEnduranceMin());
        vo.setBatteryAt(row.getBatteryAt());
        vo.setAssignedTaskCount(row.getAssignedTaskCount());
        vo.setCpuPct(row.getCpuPct());
        vo.setMemoryPct(row.getMemoryPct());
        vo.setLoadAt(row.getLoadAt());
        vo.setFaultStatus(row.getFaultStatus());
        vo.setFaultMessage(row.getFaultMessage());
        vo.setRuntimeAt(row.getRuntimeAt());
        vo.setFlightStatus(row.getFlightStatus());
        vo.setFlightStatusLabel(RobotFlightStatusEnum.labelOf(row.getFlightStatus()));

        boolean online = row.getOnlineFlag() != null && row.getOnlineFlag() == 1;
        if (row.getRuntimeAt() != null) {
            online = online && !row.getRuntimeAt().isBefore(LocalDateTime.now().minusSeconds(ONLINE_STALE_SEC));
        } else if (row.getPositionAt() != null) {
            online = !row.getPositionAt().isBefore(LocalDateTime.now().minusSeconds(ONLINE_STALE_SEC));
        }
        vo.setOnline(online);
        if (!online && !StringUtils.hasText(row.getFlightStatus())) {
            vo.setFlightStatus("OFFLINE");
            vo.setFlightStatusLabel("离线");
        }
        return vo;
    }
}
