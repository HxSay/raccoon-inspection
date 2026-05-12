package com.raccoon.cloud.system.sso.service;

import com.raccoon.cloud.system.sso.model.SsoLoginBundle;
import com.raccoon.common.result.HxResult;

import jakarta.servlet.http.HttpServletRequest;

/**
 * SSO：跳转、回调、登出、会话校验。
 */
public interface SsoService {

    /**
     * 构造统一认证中心授权地址（含 state）。
     *
     * @param returnUrl 登录成功后前端回跳地址（可选，写入一次性 state）
     */
    HxResult<?> buildAuthorizeEntry(String returnUrl);

    /**
     * 处理浏览器回调：授权码换票或 TICKET_JWT 直验，并完成本地会话签发。
     */
    SsoLoginBundle handleCallback(HttpServletRequest request, String responseMode);

    /**
     * 单点登出：清理本系统 Security 上下文，并返回 IdP 登出地址（若配置）。
     */
    HxResult<?> logoutFromSso(HttpServletRequest request);

    /**
     * 会话校验：校验 Authorization Bearer 与本地 JWT 一致性（不改变原有 JWT 机制）。
     */
    HxResult<?> validateSession(String authorizationHeader);
}
