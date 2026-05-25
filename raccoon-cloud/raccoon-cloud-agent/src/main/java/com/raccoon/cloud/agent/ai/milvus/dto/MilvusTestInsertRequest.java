package com.raccoon.cloud.agent.ai.milvus.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Milvus 测试：故障知识录入请求。
 */
@Data
public class MilvusTestInsertRequest {

    /** 文档 ID，对应 Spring AI Document.id */
    @NotBlank(message = "文档 ID 不能为空")
    private String docId;

    /** 故障处理方案等正文 */
    @NotBlank(message = "文档内容不能为空")
    private String content;

    /** 设备类型，如：主变、断路器 */
    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    /** 故障类型，如：温度异常、跳闸 */
    @NotBlank(message = "故障类型不能为空")
    private String faultType;

    /** 数据来源，默认测试录入 */
    private String source = "测试录入";
}
