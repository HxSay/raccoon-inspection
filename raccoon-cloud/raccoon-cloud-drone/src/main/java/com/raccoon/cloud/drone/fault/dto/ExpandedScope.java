package com.raccoon.cloud.drone.fault.dto;

import com.raccoon.cloud.drone.dto.GeoPoint;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class ExpandedScope {

    private Long primaryDeviceId;
    private List<Long> relatedDeviceIds = new ArrayList<>();
    private List<GeoPoint> checkPoints = new ArrayList<>();
    private Double expandCenterLon;
    private Double expandCenterLat;
    private Double expandRadiusM;

    public List<Long> getAllDeviceIds() {
        Set<Long> ids = new LinkedHashSet<>();
        if (primaryDeviceId != null) {
            ids.add(primaryDeviceId);
        }
        if (relatedDeviceIds != null) {
            ids.addAll(relatedDeviceIds);
        }
        return new ArrayList<>(ids);
    }
}
