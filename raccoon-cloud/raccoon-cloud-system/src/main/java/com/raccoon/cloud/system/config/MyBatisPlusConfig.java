package com.raccoon.cloud.system.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.raccoon.cloud.system.mapper")
public class MyBatisPlusConfig {
}
