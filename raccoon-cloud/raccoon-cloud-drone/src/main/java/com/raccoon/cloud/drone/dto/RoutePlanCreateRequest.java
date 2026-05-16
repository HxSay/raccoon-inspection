package com.raccoon.cloud.drone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RoutePlanCreateRequest {

    /** 关联巡检任务，可空 */
    private Long taskId;

    @NotNull
    private Long mapId;

    @NotNull
    private Long uavId;

    @NotBlank
    private String startPoint;

    @NotBlank
    private String endPoint;

    /**
     * 路径点（经度、纬度、高度米），按飞行顺序；为空时仅连接起终点。
     */
    private List<GeoPoint> pathPoints;

    /** 拍照航点，可为空数组 */
    private List<GeoPoint> photoPoints;

    /** 巡检设备访问顺序（设备 ID），可为空数组 */
    private List<Long> visitOrder;

    /** A*、RRT* 等，与表 algorithm 字段一致 */
    @NotBlank
    private String algorithm;
}
