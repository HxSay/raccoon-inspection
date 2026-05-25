package com.raccoon.cloud.agent.ai.milvus.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;

/**
 * 启动时自动合并 classpath:agent-milvus-test.yml，避免修改已有 application.yml。
 */
public class MilvusTestEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "agentMilvusTestYaml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("agent-milvus-test.yml");
        if (!resource.exists()) {
            return;
        }
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource);
        factory.afterPropertiesSet();
        if (CollectionUtils.isEmpty(factory.getObject())) {
            return;
        }
        Properties props = factory.getObject();
        Map<String, Object> flat = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            flat.put(name, props.getProperty(name));
        }
        // 置于最高优先级，避免 Nacos 中的 embedding 模型覆盖本测试工具配置
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, flat));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
