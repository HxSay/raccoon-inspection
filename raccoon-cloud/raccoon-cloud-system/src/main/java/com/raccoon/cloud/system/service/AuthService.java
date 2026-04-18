package com.raccoon.cloud.system.service;

import com.raccoon.cloud.system.model.dto.LoginRequest;
import com.raccoon.common.result.HxResult;

public interface AuthService {
    /**
     * 用户登录
     */
    HxResult<?> login(LoginRequest request);

    /**
     * 刷新Token
     */
    HxResult<?> refreshToken();

    /**
     * 退出登录
     */
    HxResult<?> logout();

    /**
     * 获取当前用户信息
     */
    HxResult<?> getCurrentUserInfo();
}