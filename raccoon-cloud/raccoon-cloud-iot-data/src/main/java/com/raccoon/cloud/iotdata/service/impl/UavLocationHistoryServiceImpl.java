package com.raccoon.cloud.iotdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.raccoon.cloud.iotdata.dto.UavLocationBatchRequest;
import com.raccoon.cloud.iotdata.entity.UavLocationHistory;
import com.raccoon.cloud.iotdata.mapper.UavLocationHistoryMapper;
import com.raccoon.cloud.iotdata.service.UavLocationHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UavLocationHistoryServiceImpl extends ServiceImpl<UavLocationHistoryMapper, UavLocationHistory>
        implements UavLocationHistoryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchPoints(UavLocationBatchRequest request) {
        LocalDateTime now = LocalDateTime.now();
        List<UavLocationHistory> rows = new ArrayList<>();
        for (UavLocationBatchRequest.Item item : request.getPoints()) {
            UavLocationHistory h = new UavLocationHistory();
            h.setUavId(item.getUavId());
            h.setTaskId(item.getTaskId());
            h.setMapId(item.getMapId());
            h.setLongitude(item.getLongitude());
            h.setLatitude(item.getLatitude());
            h.setHeight(item.getHeight());
            h.setSpeed(item.getSpeed());
            h.setBattery(item.getBattery());
            h.setLocationMode(item.getLocationMode());
            h.setCreateTime(item.getCreateTime() != null ? item.getCreateTime() : now);
            rows.add(h);
        }
        saveBatch(rows, 500);
    }

    @Override
    public Page<UavLocationHistory> pageByUavAndTime(
            long current,
            long size,
            Long uavId,
            LocalDateTime start,
            LocalDateTime end
    ) {
        LambdaQueryWrapper<UavLocationHistory> w = new LambdaQueryWrapper<>();
        w.eq(uavId != null, UavLocationHistory::getUavId, uavId);
        if (start != null) {
            w.ge(UavLocationHistory::getCreateTime, start);
        }
        if (end != null) {
            w.le(UavLocationHistory::getCreateTime, end);
        }
        w.orderByDesc(UavLocationHistory::getCreateTime);
        return page(new Page<>(current, size), w);
    }
}
