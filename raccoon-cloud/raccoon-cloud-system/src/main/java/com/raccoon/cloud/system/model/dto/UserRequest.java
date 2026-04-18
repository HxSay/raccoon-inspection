package com.raccoon.cloud.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "用户新增/编辑请求DTO")
public class UserRequest {
    @Schema(description = "用户ID，编辑时必填")
    private Long id;

    @Schema(description = "用户名")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "密码，新增时必填")
    private String password;

    @Schema(description = "性别:0-未知,1-男,2-女")
    private Integer gender;

    @Schema(description = "用户类型:1-C端用户,2-B端管理员")
    private Integer userType;

    @Schema(description = "状态:0-禁用,1-启用")
    private Integer status;
}