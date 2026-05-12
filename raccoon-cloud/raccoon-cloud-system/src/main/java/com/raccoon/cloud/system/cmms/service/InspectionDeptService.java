package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.cmms.dto.InspectionDeptPageRequest;
import com.raccoon.cloud.system.cmms.entity.InspectionDept;
import com.raccoon.cloud.system.cmms.mapper.InspectionDeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InspectionDeptService {

    private final InspectionDeptMapper mapper;

    public InspectionDeptService(InspectionDeptMapper mapper) {
        this.mapper = mapper;
    }

    public IPage<InspectionDept> page(InspectionDeptPageRequest req) {
        QueryWrapper<InspectionDept> w = new QueryWrapper<>();
        if (StringUtils.hasText(req.getDeptName())) {
            w.like("dept_name", req.getDeptName());
        }
        w.orderByAsc("id");
        Page<InspectionDept> p = new Page<>(req.getPage(), req.getSize());
        return mapper.selectPage(p, w);
    }

    public void save(InspectionDept row) {
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
