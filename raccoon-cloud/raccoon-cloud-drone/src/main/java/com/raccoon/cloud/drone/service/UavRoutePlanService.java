package com.raccoon.cloud.drone.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.raccoon.cloud.drone.dto.RoutePlanCreateRequest;
import com.raccoon.cloud.drone.dto.RoutePlanView;
import com.raccoon.cloud.drone.entity.UavRoutePlan;

public interface UavRoutePlanService extends IService<UavRoutePlan> {

    RoutePlanView planAndSave(RoutePlanCreateRequest request);

    RoutePlanView toView(UavRoutePlan plan);
}
