package com.raccoon.cloud.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OllamaChatRequest {

    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 覆盖 application.yml 中的默认模型 */
    private String model;

    /** 0~2，为空则使用配置默认值 */
    private Double temperature;
}
