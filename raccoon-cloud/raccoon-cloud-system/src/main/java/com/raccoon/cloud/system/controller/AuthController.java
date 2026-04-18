package com.raccoon.cloud.system.controller;

import com.raccoon.cloud.system.model.dto.LoginRequest;
import com.raccoon.cloud.system.model.dto.RegisterRequest;
import com.raccoon.cloud.system.service.AuthService;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "认证接口", description = "用户认证相关接口")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "支持用户名/手机号+密码登录")
    public HxResult<?> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "用户注册接口")
    public HxResult<?> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "刷新访问Token")
    public HxResult<?> refreshToken() {
        return authService.refreshToken();
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "用户退出登录")
    public HxResult<?> logout() {
        return authService.logout();
    }

    @PostMapping("/current")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    public HxResult<?> getCurrentUserInfo() {
        return authService.getCurrentUserInfo();
    }
}