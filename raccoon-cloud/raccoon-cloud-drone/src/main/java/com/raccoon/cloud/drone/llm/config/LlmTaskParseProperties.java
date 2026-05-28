package com.raccoon.cloud.drone.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "raccoon.llm.task-parse")
public class LlmTaskParseProperties {

    /** LLM 调用超时（毫秒） */
    private int timeoutMs = 5000;

    /** 最大重试次数 */
    private int maxRetries = 3;

    /** 输入最大长度 */
    private int maxInputLength = 500;

    /** 默认算法 */
    private String defaultAlgorithm = "RRT*";

    private long defaultPlanId = 5L;
    private long defaultTaskId = 6L;
    private long defaultUavId = 1L;
    private long defaultMapId = 1L;

    private double defaultLongitude = 104.000415;
    private double defaultLatitude = 30.5;
    private double defaultTakeoffHeight = 3.0;
    private double defaultCruiseHeight = 50.0;
}
