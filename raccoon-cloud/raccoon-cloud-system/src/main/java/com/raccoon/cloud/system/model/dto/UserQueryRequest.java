package com.raccoon.cloud.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户查询请求DTO")
public class UserQueryRequest {
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "状态:0-禁用,1-启用")
    private Integer status;

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "每页大小")
    private Integer size;
}