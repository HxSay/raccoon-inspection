package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.raccoon.cloud.system.cmms.entity.DeviceCategory;
import com.raccoon.cloud.system.cmms.mapper.DeviceCategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceCategoryService {

    private final DeviceCategoryMapper mapper;

    public DeviceCategoryService(DeviceCategoryMapper mapper) {
        this.mapper = mapper;
    }

    public List<DeviceCategory> listAll() {
        return mapper.selectList(new QueryWrapper<DeviceCategory>().orderByAsc("sort", "id"));
    }

    public void save(DeviceCategory row) {
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
