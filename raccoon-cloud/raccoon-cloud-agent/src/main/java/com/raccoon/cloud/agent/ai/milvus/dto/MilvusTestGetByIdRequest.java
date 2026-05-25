package com.raccoon.cloud.agent.ai.milvus.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Milvus 测试：按文档 ID 查询请求。
 */
@Data
public class MilvusTestGetByIdRequest {

    @NotBlank(message = "文档 ID 不能为空")
    private String docId;
}
