package com.raccoon.cloud.iotdata.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.raccoon.cloud.iotdata.dto.UavLocationBatchRequest;
import com.raccoon.cloud.iotdata.entity.UavLocationHistory;

import java.time.LocalDateTime;

public interface UavLocationHistoryService extends IService<UavLocationHistory> {

    void saveBatchPoints(UavLocationBatchRequest request);

    Page<UavLocationHistory> pageByUavAndTime(
            long current,
            long size,
            Long uavId,
            LocalDateTime start,
            LocalDateTime end
    );
}
