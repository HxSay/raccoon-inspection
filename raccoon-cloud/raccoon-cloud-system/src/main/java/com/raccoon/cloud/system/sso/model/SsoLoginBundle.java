package com.raccoon.cloud.system.sso.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * SSO 登录成功后的返回载体：JSON 载荷与可选的浏览器重定向地址。
 */
@Data
@Builder
public class SsoLoginBundle {

    /**
     * 与本地 /auth/login 成功时结构保持一致：token、refreshToken、user。
     */
    private Map<String, Object> tokenPayload;

    /**
     * 若不为空，表示需要浏览器 302 到该完整 URL（已拼接 query）。
     */
    private String browserRedirectUrl;
}
