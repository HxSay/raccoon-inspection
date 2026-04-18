package com.raccoon.common.feign.device;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.raccoon.common.result.HxResult;

@FeignClient("raccoon-cloud-device")
public interface DeviceFeignClient {
    /**
     * 跨服务查询设备名称
     * 接口路径要和后面 device 服务的接口路径完全一致
     */
    @GetMapping("/device/name/{deviceId}")
    HxResult<String> getDeviceName(@PathVariable("deviceId") Long deviceId);
}
