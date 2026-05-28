package com.raccoon.cloud.drone.service;

import com.raccoon.cloud.drone.dto.InspectionRobotRuntimeVO;
import com.raccoon.cloud.drone.dto.InspectionRobotTelemetryReport;

import java.util.List;
import java.util.Map;

public interface InspectionRobotRuntimeService {

    void report(InspectionRobotTelemetryReport report);

    void reportBatch(List<InspectionRobotTelemetryReport> reports);

    InspectionRobotRuntimeVO getRuntime(Long uavId, String workRangeDesc);

    Map<Long, InspectionRobotRuntimeVO> getRuntimeMap(List<Long> uavIds, Map<Long, String> workRangeByUav);
}
