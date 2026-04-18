package com.raccoon.cloud.device.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raccoon.common.result.HxResult;

@RestController
@RequestMapping("/device")
public class DeviceTestController {

    /**
     * 测试接口：浏览器访问 localhost:8085/device/test
     */
    @GetMapping("/test")
    public HxResult<String> test() {
        return HxResult.success("项目搭建成功！raccoon-cloud-device 服务启动正常 ✅");
    }

    /**
     * 跨服务查询设备名称
     * 路径要和Feign接口里的路径完全一致
     */
    @GetMapping("/name/{deviceId}")
    public HxResult<String> getDeviceName(@PathVariable("deviceId") Long deviceId) {
        // 这里我们先写死模拟数据，实际项目里查数据库就行
        String deviceName = "巡检机器人-" + deviceId;
        return HxResult.success(deviceName);
    }

}