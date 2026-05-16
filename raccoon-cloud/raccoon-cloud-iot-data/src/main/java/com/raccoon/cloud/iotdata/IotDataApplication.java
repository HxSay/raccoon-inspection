package com.raccoon.cloud.iotdata;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.raccoon.cloud.iotdata.mapper")
public class IotDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotDataApplication.class, args);
    }
}
