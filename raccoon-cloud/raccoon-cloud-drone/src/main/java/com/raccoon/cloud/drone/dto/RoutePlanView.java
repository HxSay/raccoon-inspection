package com.raccoon.cloud.drone.dto;

import com.raccoon.cloud.drone.entity.UavRoutePlan;
import lombok.Data;

@Data
public class RoutePlanView {

    private UavRoutePlan plan;
    private UavRouteDispatchPayload dispatch;
}
