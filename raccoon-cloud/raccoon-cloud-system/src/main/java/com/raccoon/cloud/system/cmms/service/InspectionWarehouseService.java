package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.cmms.dto.CmmsPageRequest;
import com.raccoon.cloud.system.cmms.entity.InspectionWarehouse;
import com.raccoon.cloud.system.cmms.mapper.InspectionWarehouseMapper;
import org.springframework.stereotype.Service;

@Service
public class InspectionWarehouseService {

    private final InspectionWarehouseMapper mapper;

    public InspectionWarehouseService(InspectionWarehouseMapper mapper) {
        this.mapper = mapper;
    }

    public IPage<InspectionWarehouse> page(CmmsPageRequest req) {
        Page<InspectionWarehouse> p = new Page<>(req.getPage(), req.getSize());
        return mapper.selectPage(p, new QueryWrapper<InspectionWarehouse>().orderByDesc("id"));
    }

    public void save(InspectionWarehouse row) {
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
