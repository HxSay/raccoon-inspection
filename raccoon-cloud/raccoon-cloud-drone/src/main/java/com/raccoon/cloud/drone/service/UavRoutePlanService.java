package com.raccoon.cloud.drone.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.raccoon.cloud.drone.dto.RoutePlanCreateRequest;
import com.raccoon.cloud.drone.dto.RoutePlanView;
import com.raccoon.cloud.drone.dto.UavRouteDispatchPayload;
import com.raccoon.cloud.drone.entity.UavRoutePlan;

public interface UavRoutePlanService extends IService<UavRoutePlan> {

    RoutePlanView planAndSave(RoutePlanCreateRequest request);

    RoutePlanView toView(UavRoutePlan plan);

    /** 按无人机与路径规划 ID 查询下发 JSON */
    UavRouteDispatchPayload getDispatchByUavAndPlan(Long uavId, Long planId);
}
