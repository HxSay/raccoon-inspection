package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.cmms.dto.SupplierPageRequest;
import com.raccoon.cloud.system.cmms.entity.Supplier;
import com.raccoon.cloud.system.cmms.mapper.SupplierMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SupplierService {

    private final SupplierMapper mapper;

    public SupplierService(SupplierMapper mapper) {
        this.mapper = mapper;
    }

    public IPage<Supplier> page(SupplierPageRequest req) {
        QueryWrapper<Supplier> w = new QueryWrapper<>();
        if (StringUtils.hasText(req.getSupplierName())) {
            w.like("supplier_name", req.getSupplierName());
        }
        w.orderByDesc("id");
        Page<Supplier> p = new Page<>(req.getPage(), req.getSize());
        return mapper.selectPage(p, w);
    }

    public void save(Supplier row) {
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
