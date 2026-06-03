package com.raccoon.cloud.drone.fault.service;

import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskRequest;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskResponse;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskTypeEnum;
import com.raccoon.cloud.drone.dispatch.service.TaskGenerateService;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.fault.dto.ExpandedScope;
import com.raccoon.cloud.drone.fault.dto.FaultResponsePlan;
import com.raccoon.cloud.drone.fault.dto.ReinspectDispatchResult;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import com.raccoon.cloud.drone.mapper.UavInspectionDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 复巡任务插单：构建 RE_INSPECTION 调度请求并走拍卖分配链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReinspectTaskDispatchService {

    private final TaskGenerateService taskGenerateService;
    private final FaultAlarmService faultAlarmService;
    private final UavInspectionDeviceMapper deviceMapper;

    public ReinspectDispatchResult dispatch(FaultEvent event, FaultResponsePlan plan, ExpandedScope scope) {
        if (event.getMapId() == null) {
            UavInspectionDevice dev = event.getDeviceId() != null
                    ? deviceMapper.selectById(event.getDeviceId()) : null;
            if (dev != null) {
                event.setMapId(dev.getMapId());
            }
        }
        if (event.getMapId() == null) {
            return ReinspectDispatchResult.failed("缺少 mapId，无法插单复巡");
        }
        DispatchTaskRequest req = new DispatchTaskRequest();
        req.setRequestId("FAULT-" + event.getEventId());
        req.setTaskType(DispatchTaskTypeEnum.RE_INSPECTION.getCode());
        req.setMapId(event.getMapId());
        req.setDeviceIds(new ArrayList<>(scope.getAllDeviceIds()));
        req.setWaypoints(scope.getCheckPoints());
        req.setPriority(plan.getLevel() == FaultLevel.CRITICAL ? "CRITICAL" : "URGENT");
        req.setRemark("故障复巡 eventId=" + event.getEventId() + " type=" + event.getFaultType());
        req.setEnableSimulation(plan.getLevel() != FaultLevel.GENERAL);
        req.setAutoDispatch(true);
        Map<String, Object> ext = new HashMap<>();
        ext.put("sourceFaultEventId", event.getEventId());
        ext.put("requireMultiModal", plan.isRequireMultiModal());
        ext.put("pauseOtherTasks", plan.isPauseOtherTasks());
        req.setExtension(ext);
        if (event.getSourceTerminalId() != null) {
            req.setPreferredTerminalIds(List.of(event.getSourceTerminalId()));
        }
        try {
            DispatchTaskResponse resp = taskGenerateService.generate(req);
            faultAlarmService.pushByLevel(plan.getLevel(), event, plan);
            if (resp.isAssigned()) {
                return ReinspectDispatchResult.success(resp.getTaskId(), resp.getAssignedTerminalId());
            }
            return ReinspectDispatchResult.failed(resp.getMessage() != null
                    ? resp.getMessage() : "复巡任务未分配到终端");
        } catch (Exception e) {
            log.error("[fault-reinspect] 调度失败 eventId={}", event.getEventId(), e);
            return ReinspectDispatchResult.failed(e.getMessage());
        }
    }
}
