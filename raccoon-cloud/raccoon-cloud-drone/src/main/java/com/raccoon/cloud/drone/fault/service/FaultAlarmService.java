package com.raccoon.cloud.drone.fault.service;

import com.raccoon.cloud.drone.fault.dto.FaultResponsePlan;
import com.raccoon.cloud.drone.fault.enums.FaultLevel;
import com.raccoon.cloud.drone.fault.model.FaultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 分级告警推送（当前落日志，可对接 APP/短信/声光）。
 */
@Slf4j
@Service
public class FaultAlarmService {

    public void pushByLevel(FaultLevel level, FaultEvent event, FaultResponsePlan plan) {
        log.warn("[fault-alarm] level={} eventId={} deviceId={} action={} desc={}",
                level.getCode(), event.getEventId(), event.getDeviceId(),
                plan.getAction() != null ? plan.getAction().getCode() : "-",
                event.getDescription());
        if (level == FaultLevel.CRITICAL) {
            log.error("[fault-alarm] 紧急故障需立即人工确认 eventId={}", event.getEventId());
        }
    }
}
