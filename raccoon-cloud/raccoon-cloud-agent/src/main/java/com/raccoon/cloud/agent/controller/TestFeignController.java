package com.raccoon.cloud.agent.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raccoon.common.feign.device.DeviceFeignClient;
import com.raccoon.common.result.HxResult;

@RestController
@RequestMapping("/test")
public class TestFeignController {
   // 👇 注入Feign客户端，就像用本地Service一样
    @Autowired
    private DeviceFeignClient deviceFeignClient;

    /**
     * 测试跨服务调用
     * 访问这个接口，会自动调用 device 服务的接口
     */
    @GetMapping("/feign/{deviceId}")
    public HxResult<String> testFeign(@PathVariable("deviceId") Long deviceId) {
        // 跨服务调用！就这么简单
        HxResult<String> deviceNameResult = deviceFeignClient.getDeviceName(deviceId);
        
        // 把结果包装一下返回
        return HxResult.success("调用成功！设备名称是：" + deviceNameResult.getData());
    }
}
