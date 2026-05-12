package com.raccoon.cloud.system.sso.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * SSO 模块 Spring 配置：属性绑定、通用 Bean。
 */
@Configuration
@EnableConfigurationProperties(SsoProperties.class)
public class SsoConfiguration {

    @Bean
    public RestTemplate ssoRestTemplate() {
        return new RestTemplate();
    }
}
