package com.raccoon.cloud.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
@Schema(description = "角色新增/编辑请求DTO")
public class RoleRequest {
    @Schema(description = "角色ID，编辑时必填")
    private Long id;

    @Schema(description = "角色名称")
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    @Schema(description = "角色编码")
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    @Schema(description = "角色描述")
    private String description;

    @Schema(description = "排序号")
    private Integer sort;

    @Schema(description = "状态:0-禁用,1-启用")
    private Integer status;
}