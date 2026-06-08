package com.raccoon.cloud.drone.closeloop.dto;

import com.raccoon.cloud.drone.closeloop.enums.TerminalExecStatus;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端执行结果模型（步骤1：多机巡检结果回收）。
 */
@Data
public class TerminalTaskResultDTO {

    private Long terminalId;
    private String deviceType;
    private TerminalExecStatus status;

    private Integer plannedPointCount;
    private Integer finishedPointCount;
    private Integer uploadedDataCount;
    private Double flightDistanceM;

    private List<Long> finishedDeviceIds = new ArrayList<>();
    private List<Long> missedDeviceIds = new ArrayList<>();
    private String failReason;
}
