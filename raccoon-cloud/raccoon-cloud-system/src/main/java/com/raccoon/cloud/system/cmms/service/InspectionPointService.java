package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.raccoon.cloud.system.cmms.entity.InspectionPoint;
import com.raccoon.cloud.system.cmms.mapper.InspectionPointMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InspectionPointService {

    private final InspectionPointMapper mapper;

    public InspectionPointService(InspectionPointMapper mapper) {
        this.mapper = mapper;
    }

    public List<InspectionPoint> listByDevice(Long deviceId) {
        return mapper.selectList(new QueryWrapper<InspectionPoint>()
                .eq("device_id", deviceId)
                .orderByAsc("sort", "id"));
    }

    public void save(InspectionPoint row) {
        if (row.getSort() == null) {
            row.setSort(0);
        }
        if (row.getId() == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
