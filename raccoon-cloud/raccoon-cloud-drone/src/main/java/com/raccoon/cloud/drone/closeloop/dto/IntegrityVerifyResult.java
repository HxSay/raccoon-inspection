package com.raccoon.cloud.drone.closeloop.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 完整性核对结果（步骤2 输出）：点位·设备·多模态·轨迹。
 */
@Data
public class IntegrityVerifyResult {

    private String taskId;

    private List<Long> missedDeviceIds = new ArrayList<>();
    private int missedWaypointCount;
    private List<DataGap> dataGaps = new ArrayList<>();
    private boolean trackComplete = true;

    private boolean passed;
    /** 综合完成率 0~1 */
    private double completionRate;
}
