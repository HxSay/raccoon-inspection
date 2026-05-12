package com.raccoon.cloud.system.cmms.schedule;

import com.raccoon.cloud.system.cmms.service.InspectionTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 巡检任务到达计划执行时间后，将已关联且仍为「待下发」的巡检工单自动下发到执行人（手机端待办）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InspectionTaskDispatchScheduler {

    private final InspectionTaskService inspectionTaskService;

    @Scheduled(fixedDelay = 60_000)
    public void dispatchDueWorkOrders() {
        try {
            int n = inspectionTaskService.dispatchDueLinkedWorkOrders();
            if (n > 0) {
                log.info("巡检任务到点自动派发工单 {} 条", n);
            }
        } catch (Exception e) {
            log.warn("巡检任务到点派发扫描失败", e);
        }
    }
}
