package com.raccoon.cloud.agent.ai.milvus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Milvus 测试：向量相似检索请求。
 */
@Data
public class MilvusTestSearchRequest {

    @NotBlank(message = "查询词不能为空")
    private String query;

    /** 返回条数，默认 3 */
    @Min(value = 1, message = "topK 至少为 1")
    @Max(value = 50, message = "topK 最多为 50")
    private Integer topK = 3;
}
