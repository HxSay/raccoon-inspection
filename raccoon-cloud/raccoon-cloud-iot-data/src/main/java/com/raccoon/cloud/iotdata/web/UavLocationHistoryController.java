package com.raccoon.cloud.iotdata.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.iotdata.dto.UavLocationBatchRequest;
import com.raccoon.cloud.iotdata.entity.UavLocationHistory;
import com.raccoon.cloud.iotdata.service.UavLocationHistoryService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/uav-location")
@RequiredArgsConstructor
public class UavLocationHistoryController {

    private final UavLocationHistoryService uavLocationHistoryService;

    @PostMapping("/batch")
    public HxResult<Void> batch(@Valid @RequestBody UavLocationBatchRequest request) {
        uavLocationHistoryService.saveBatchPoints(request);
        return HxResult.success();
    }

    @GetMapping("/page")
    public HxResult<Page<UavLocationHistory>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "50") long size,
            @RequestParam Long uavId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        return HxResult.success(uavLocationHistoryService.pageByUavAndTime(current, size, uavId, start, end));
    }
}
