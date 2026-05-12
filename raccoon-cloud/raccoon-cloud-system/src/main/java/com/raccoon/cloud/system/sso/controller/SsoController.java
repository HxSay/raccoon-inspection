package com.raccoon.cloud.system.sso.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.system.sso.exception.SsoException;
import com.raccoon.cloud.system.sso.model.SsoLoginBundle;
import com.raccoon.cloud.system.sso.service.SsoService;
import com.raccoon.common.result.HxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * SSO 对外接口：跳转入口、回调、登出、会话校验。
 */
@Slf4j
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
@Tag(name = "SSO 单点登录", description = "统一认证集成")
public class SsoController {

    private final SsoService ssoService;
    private final ObjectMapper objectMapper;

    @GetMapping("/entry")
    @Operation(summary = "SSO 跳转入口", description = "生成 state 并返回 IdP 授权地址（JSON）")
    public HxResult<?> entry(@RequestParam(value = "returnUrl", required = false) String returnUrl) {
        try {
            return ssoService.buildAuthorizeEntry(returnUrl);
        } catch (SsoException e) {
            log.warn("SSO entry 失败: {}", e.getMessage());
            return HxResult.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("SSO entry 异常", e);
            return HxResult.fail("SSO 入口异常");
        }
    }

    @GetMapping("/entry/redirect")
    @Operation(summary = "SSO 跳转入口（302）", description = "直接重定向浏览器到统一认证中心")
    public void entryRedirect(
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            HttpServletResponse response) throws IOException {
        try {
            HxResult<?> r = ssoService.buildAuthorizeEntry(returnUrl);
            if (r.getCode() != 200 || r.getData() == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, r.getMsg());
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) r.getData();
            String location = data.get("redirectUrl");
            if (!StringUtils.hasText(location)) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "缺少 redirectUrl");
                return;
            }
            response.sendRedirect(location);
        } catch (SsoException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/callback")
    @Operation(summary = "SSO 回调", description = "OAuth2 授权码或 TICKET_JWT 票据处理，签发本地 JWT")
    public void callback(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(value = "responseMode", defaultValue = "json") String responseMode) throws IOException {
        try {
            SsoLoginBundle bundle = ssoService.handleCallback(request, responseMode);
            if ("redirect".equalsIgnoreCase(responseMode) && StringUtils.hasText(bundle.getBrowserRedirectUrl())) {
                response.sendRedirect(bundle.getBrowserRedirectUrl());
                return;
            }
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(HxResult.success(bundle.getTokenPayload())));
        } catch (SsoException e) {
            log.warn("SSO callback 失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(HxResult.fail(e.getMessage())));
        } catch (Exception e) {
            log.error("SSO callback 异常", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(HxResult.fail("SSO 回调处理异常")));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "SSO 登出", description = "销毁本地会话，并返回 IdP 登出地址（若配置）")
    public HxResult<?> logout(HttpServletRequest request) {
        try {
            return ssoService.logoutFromSso(request);
        } catch (SsoException e) {
            return HxResult.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("SSO logout 异常", e);
            return HxResult.fail("SSO 登出异常");
        }
    }

    @GetMapping("/session")
    @Operation(summary = "会话校验", description = "校验 Authorization Bearer 本地 JWT 是否有效")
    public HxResult<?> session(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ssoService.validateSession(authorization);
    }
}
