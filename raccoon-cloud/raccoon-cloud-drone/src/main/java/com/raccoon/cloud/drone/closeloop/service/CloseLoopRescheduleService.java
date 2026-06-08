package com.raccoon.cloud.drone.closeloop.service;

import com.raccoon.cloud.drone.closeloop.dto.CloseLoopReportRequest;
import com.raccoon.cloud.drone.closeloop.dto.DataGap;
import com.raccoon.cloud.drone.closeloop.dto.IntegrityVerifyResult;
import com.raccoon.cloud.drone.closeloop.dto.RescheduleResult;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskRequest;
import com.raccoon.cloud.drone.dispatch.dto.DispatchTaskResponse;
import com.raccoon.cloud.drone.dispatch.enums.DispatchTaskTypeEnum;
import com.raccoon.cloud.drone.dispatch.service.TaskGenerateService;
import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import com.raccoon.cloud.drone.entity.UavInspectionDevice;
import com.raccoon.cloud.drone.mapper.UavInspectionDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 步骤5：未完成项自动重调度。
 * <p>核对为 PARTIAL 时，构建仅含未完成项的补检任务，走拍卖分配 + 路径规划链路，返回可下发仿真的航线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloseLoopRescheduleService {

    private final TaskGenerateService taskGenerateService;
    private final UavInspectionDeviceMapper deviceMapper;

    @Value("${raccoon.closeloop.max-reschedule-retry:2}")
    private int maxRescheduleRetry;

    public int getMaxRescheduleRetry() {
        return maxRescheduleRetry;
    }

    public RescheduleResult reschedule(CloseLoopReportRequest req, IntegrityVerifyResult verify) {
        if (verify.isPassed()) {
            return RescheduleResult.notNeeded();
        }
        RescheduleResult out = new RescheduleResult();
        out.setNeeded(true);

        // 未完成设备 = 漏检设备 ∪ 存在数据缺口的设备
        Set<Long> targetDevices = new LinkedHashSet<>(verify.getMissedDeviceIds());
        for (DataGap g : verify.getDataGaps()) {
            if (g.getDeviceId() != null) {
                targetDevices.add(g.getDeviceId());
            }
        }
        if (targetDevices.isEmpty() && req.getPlannedDeviceIds() != null) {
            // 仅航点漏检但无法定位设备时，对全部计划设备补检
            targetDevices.addAll(req.getPlannedDeviceIds());
        }
        out.setMissedDeviceIds(new ArrayList<>(targetDevices));

        if (req.getMapId() == null) {
            out.setSuccess(false);
            out.setMessage("缺少 mapId，无法插单补检");
            return out;
        }
        if (targetDevices.isEmpty()) {
            out.setSuccess(false);
            out.setMessage("无可定位的未完成设备，转人工核对");
            return out;
        }

        List<Long> deviceIds = new ArrayList<>(targetDevices);
        List<String> deviceNames = resolveDeviceNames(deviceIds);

        DispatchTaskRequest dr = new DispatchTaskRequest();
        dr.setRequestId("CLOSELOOP-" + req.getTaskId() + "-R" + (safe(req.getRescheduleCount()) + 1));
        dr.setTaskType(DispatchTaskTypeEnum.RE_INSPECTION.getCode());
        dr.setMapId(req.getMapId());
        dr.setDeviceIds(deviceIds);
        dr.setDeviceNames(deviceNames);
        dr.setPriority("URGENT");
        dr.setRemark("自动补检：漏检设备 " + verify.getMissedDeviceIds().size()
                + " 个，数据缺口 " + verify.getDataGaps().size() + " 项（父任务 " + req.getTaskId() + "）");
        dr.setEnableSimulation(true);
        dr.setAutoDispatch(true);
        Map<String, Object> ext = new HashMap<>();
        ext.put("closeLoopParentTaskId", req.getTaskId());
        ext.put("rescheduleCount", safe(req.getRescheduleCount()) + 1);
        dr.setExtension(ext);

        try {
            DispatchTaskResponse resp = taskGenerateService.generate(dr);
            if (resp.isAssigned()) {
                out.setSuccess(true);
                out.setReinspectTaskId(resp.getTaskId());
                out.setAssignedTerminalId(resp.getAssignedTerminalId());
                out.setRoutePayload(resp.getWorkOrder() != null ? resp.getWorkOrder().getPayload() : null);
                out.setMessage("已生成补检任务，纳入 " + deviceIds.size() + " 个未完成设备");
            } else {
                out.setSuccess(false);
                out.setMessage(resp.getMessage() != null ? resp.getMessage() : "补检任务未分配到终端");
            }
        } catch (Exception e) {
            log.error("[closeloop-reschedule] 补检调度失败 taskId={}", req.getTaskId(), e);
            out.setSuccess(false);
            out.setMessage("补检调度失败：" + e.getMessage());
        }
        return out;
    }

    private int safe(Integer i) {
        return i != null ? i : 0;
    }

    private List<String> resolveDeviceNames(List<Long> deviceIds) {
        List<String> names = new ArrayList<>();
        for (Long id : deviceIds) {
            UavInspectionDevice dev = deviceMapper.selectById(id);
            if (dev != null && dev.getDeviceName() != null && !dev.getDeviceName().isBlank()) {
                names.add(dev.getDeviceName().trim());
            }
        }
        return names;
    }
}
