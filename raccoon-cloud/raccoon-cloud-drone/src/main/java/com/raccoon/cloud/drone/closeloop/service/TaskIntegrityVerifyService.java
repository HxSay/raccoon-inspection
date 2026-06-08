package com.raccoon.cloud.drone.closeloop.service;

import com.raccoon.cloud.drone.closeloop.dto.CapturedPointDTO;
import com.raccoon.cloud.drone.closeloop.dto.CloseLoopReportRequest;
import com.raccoon.cloud.drone.closeloop.dto.DataGap;
import com.raccoon.cloud.drone.closeloop.dto.IntegrityVerifyResult;
import com.raccoon.cloud.drone.closeloop.dto.TaskCollectSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 步骤2：完整性核对引擎。
 * <p>对照计划快照（点位/设备/多模态类型），逐项核对实际执行情况。
 */
@Slf4j
@Service
public class TaskIntegrityVerifyService {

    private static final List<String> DEFAULT_REQUIRED = List.of("VISIBLE");

    public IntegrityVerifyResult verify(CloseLoopReportRequest req, TaskCollectSummary summary) {
        IntegrityVerifyResult result = new IntegrityVerifyResult();
        result.setTaskId(req.getTaskId());

        // ① 点位核对：计划航点 vs 实际完成航点
        int planned = summary.getPlannedWaypointCount();
        int finished = summary.getFinishedWaypointCount();
        int missedWp = Math.max(0, planned - finished);
        result.setMissedWaypointCount(missedWp);

        // ② 设备核对：计划设备 vs 实际巡检设备
        List<Long> plannedDevices = req.getPlannedDeviceIds() != null
                ? req.getPlannedDeviceIds() : List.of();
        Set<Long> finishedDevices = summary.getAllFinishedDeviceIds();
        List<Long> missedDevices = new ArrayList<>();
        if (!finishedDevices.isEmpty() || !plannedDevices.isEmpty()) {
            for (Long d : plannedDevices) {
                if (!finishedDevices.contains(d)) {
                    missedDevices.add(d);
                }
            }
        }
        // 设备未上报 deviceId 时，退化为「按访问顺序，超出完成航点数的计划设备视为漏检」
        if (missedDevices.isEmpty() && finishedDevices.isEmpty() && missedWp > 0
                && plannedDevices.size() >= finished) {
            for (int i = finished; i < plannedDevices.size(); i++) {
                missedDevices.add(plannedDevices.get(i));
            }
        }
        result.setMissedDeviceIds(missedDevices);

        // ③ 多模态数据核对：每个完成点位是否具备要求的数据类型
        List<String> requiredTypes = req.getRequiredDataTypes() != null && !req.getRequiredDataTypes().isEmpty()
                ? normalize(req.getRequiredDataTypes()) : DEFAULT_REQUIRED;
        List<DataGap> gaps = new ArrayList<>();
        for (CapturedPointDTO p : summary.getCapturedPoints()) {
            Set<String> actual = new LinkedHashSet<>(normalize(p.getDataTypes()));
            for (String req2 : requiredTypes) {
                if (!actual.contains(req2)) {
                    gaps.add(new DataGap(p.getWaypointIndex(), p.getDeviceId(), req2,
                            "缺失 " + req2 + " 数据"));
                }
            }
        }
        result.setDataGaps(gaps);

        // ④ 轨迹核对：完成航点不少于计划航点视为轨迹完整
        result.setTrackComplete(finished >= planned);

        // ⑤ 综合判定
        boolean pointComplete = missedWp == 0;
        boolean deviceComplete = missedDevices.isEmpty();
        boolean dataComplete = gaps.isEmpty();
        result.setPassed(pointComplete && deviceComplete && dataComplete && result.isTrackComplete());
        result.setCompletionRate(calcCompletionRate(planned, finished, plannedDevices.size(), missedDevices.size(), gaps.size(), summary.getCapturedPoints().size()));

        log.info("[closeloop-verify] taskId={} passed={} rate={} missedWp={} missedDev={} gaps={}",
                req.getTaskId(), result.isPassed(), String.format("%.2f", result.getCompletionRate()),
                missedWp, missedDevices.size(), gaps.size());
        return result;
    }

    private double calcCompletionRate(int plannedWp, int finishedWp, int plannedDev, int missedDev,
                                      int gapCount, int capturedCount) {
        double wpRate = plannedWp > 0 ? Math.min(1.0, (double) finishedWp / plannedWp) : 1.0;
        double devRate = plannedDev > 0 ? Math.max(0.0, (double) (plannedDev - missedDev) / plannedDev) : 1.0;
        double dataRate = capturedCount > 0 ? Math.max(0.0, 1.0 - (double) gapCount / Math.max(1, capturedCount)) : 1.0;
        double rate = wpRate * 0.5 + devRate * 0.3 + dataRate * 0.2;
        return Math.round(rate * 1000) / 1000.0;
    }

    private List<String> normalize(List<String> types) {
        if (types == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String t : types) {
            if (t != null && !t.isBlank()) {
                out.add(t.trim().toUpperCase());
            }
        }
        return out;
    }
}
