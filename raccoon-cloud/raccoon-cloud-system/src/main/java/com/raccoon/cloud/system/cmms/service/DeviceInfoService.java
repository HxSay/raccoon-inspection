package com.raccoon.cloud.system.cmms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.cmms.dto.DeviceInfoPageRequest;
import com.raccoon.cloud.system.cmms.entity.DeviceInfo;
import com.raccoon.cloud.system.cmms.mapper.DeviceInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceInfoService {

    private final DeviceInfoMapper mapper;

    public DeviceInfoService(DeviceInfoMapper mapper) {
        this.mapper = mapper;
    }

    public IPage<DeviceInfo> page(DeviceInfoPageRequest req) {
        QueryWrapper<DeviceInfo> w = new QueryWrapper<>();
        if (StringUtils.hasText(req.getDeviceName())) {
            w.like("device_name", req.getDeviceName());
        }
        if (StringUtils.hasText(req.getDeviceCode())) {
            w.like("device_code", req.getDeviceCode());
        }
        if (req.getStatus() != null) {
            w.eq("status", req.getStatus());
        }
        if (req.getCategoryId() != null) {
            w.eq("category_id", req.getCategoryId());
        }
        w.orderByDesc("id");
        Page<DeviceInfo> p = new Page<>(req.getPage(), req.getSize());
        return mapper.selectPage(p, w);
    }

    public DeviceInfo get(Long id) {
        return mapper.selectById(id);
    }

    /**
     * 按设备编号精确查询（扫码核对）。
     */
    public DeviceInfo getByDeviceCode(String deviceCode) {
        if (!StringUtils.hasText(deviceCode)) {
            return null;
        }
        return mapper.selectOne(new QueryWrapper<DeviceInfo>().eq("device_code", deviceCode.trim()));
    }

    public void save(DeviceInfo row) {
        if (row.getStatus() == null) {
            row.setStatus(1);
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
