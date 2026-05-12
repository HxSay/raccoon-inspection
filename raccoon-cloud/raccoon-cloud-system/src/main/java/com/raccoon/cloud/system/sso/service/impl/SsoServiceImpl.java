package com.raccoon.cloud.system.sso.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.raccoon.cloud.system.mapper.ThirdPartyUserMapper;
import com.raccoon.cloud.system.mapper.UserMapper;
import com.raccoon.cloud.system.model.ThirdPartyUser;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.service.RoleService;
import com.raccoon.cloud.system.service.UserService;
import com.raccoon.cloud.system.sso.config.SsoProperties;
import com.raccoon.cloud.system.sso.constant.SsoConstants;
import com.raccoon.cloud.system.sso.exception.SsoException;
import com.raccoon.cloud.system.sso.model.ResolvedSsoTicket;
import com.raccoon.cloud.system.sso.model.SsoLoginBundle;
import com.raccoon.cloud.system.sso.service.SsoService;
import com.raccoon.cloud.system.sso.util.IdpJwtVerifier;
import com.raccoon.cloud.system.sso.util.SsoOAuth2TokenClient;
import com.raccoon.cloud.system.sso.util.SsoReplayGuard;
import com.raccoon.cloud.system.sso.util.SsoStateStore;
import com.raccoon.cloud.system.sso.util.SsoStateStore.Payload;
import com.raccoon.cloud.system.utils.JwtUtil;
import com.raccoon.common.result.HxResult;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SSO 核心业务：OAuth2 授权码 / TICKET_JWT、验签、防重放、用户绑定与自动注册、本地 JWT 签发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoServiceImpl implements SsoService {

    private static final String LOGIN_CHANNEL_SSO = "sso";

    private final SsoProperties ssoProperties;
    private final SsoStateStore ssoStateStore;
    private final SsoOAuth2TokenClient ssoOAuth2TokenClient;
    private final IdpJwtVerifier idpJwtVerifier;
    private final SsoReplayGuard ssoReplayGuard;
    private final UserMapper userMapper;
    private final ThirdPartyUserMapper thirdPartyUserMapper;
    private final UserService userService;
    private final RoleService roleService;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public HxResult<?> buildAuthorizeEntry(String returnUrl) {
        assertEnabled();
        assertOAuthConfigPresent();
        String state = ssoStateStore.issueState(returnUrl);
        String url = UriComponentsBuilder.fromHttpUrl(ssoProperties.getAuthorizationUri())
                .queryParam(SsoConstants.PARAM_RESPONSE_TYPE, "code")
                .queryParam(SsoConstants.PARAM_CLIENT_ID, ssoProperties.getClientId())
                .queryParam(SsoConstants.PARAM_REDIRECT_URI, ssoProperties.getRedirectUri())
                .queryParam(SsoConstants.PARAM_SCOPE, ssoProperties.getScope())
                .queryParam(SsoConstants.PARAM_STATE, state)
                .build(true)
                .toUriString();
        Map<String, String> data = new HashMap<>();
        data.put("redirectUrl", url);
        return HxResult.success(data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SsoLoginBundle handleCallback(HttpServletRequest request, String responseMode) {
        assertEnabled();
        String error = request.getParameter(SsoConstants.PARAM_ERROR);
        if (StringUtils.hasText(error)) {
            String desc = request.getParameter(SsoConstants.PARAM_ERROR_DESCRIPTION);
            throw new SsoException("SSO_IDP_ERROR", "IdP 返回错误: " + error + (StringUtils.hasText(desc) ? (" - " + desc) : ""));
        }

        SsoProperties.SsoMode mode = ssoProperties.getMode() == null ? SsoProperties.SsoMode.OAUTH2_CODE : ssoProperties.getMode();
        ResolvedSsoTicket resolved = resolveRawJwt(request, mode);
        String jwt = resolved.getRawJwt();
        Claims claims = idpJwtVerifier.verifyAndParseClaims(jwt, ssoProperties);
        ssoReplayGuard.assertAndConsumeTicket(claims, jwt);

        String sub = claims.getSubject();
        if (!StringUtils.hasText(sub)) {
            throw new SsoException("SSO_SUB_MISSING", "JWT 缺少 sub");
        }
        String email = claims.get(SsoConstants.CLAIM_EMAIL, String.class);
        String displayName = resolveDisplayName(claims);

        User user = bindOrProvisionUser(sub, email, displayName);

        updateUserLoginMeta(user, request);
        upsertThirdPartyBinding(user.getId(), sub, email, displayName);

        Map<String, Object> tokenPayload = issueLocalTokens(user);
        String redirectBase = resolvePostLoginRedirectBase(request, resolved.getReturnUrlFromState());
        String browserRedirectUrl = null;
        if ("redirect".equalsIgnoreCase(responseMode) && StringUtils.hasText(redirectBase)) {
            browserRedirectUrl = UriComponentsBuilder.fromHttpUrl(redirectBase)
                    .queryParam("token", tokenPayload.get("token"))
                    .queryParam("refreshToken", tokenPayload.get("refreshToken"))
                    .build(true)
                    .toUriString();
        }
        return SsoLoginBundle.builder()
                .tokenPayload(tokenPayload)
                .browserRedirectUrl(browserRedirectUrl)
                .build();
    }

    @Override
    public HxResult<?> logoutFromSso(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        Map<String, Object> data = new HashMap<>();
        data.put("localSessionCleared", true);
        if (ssoProperties.isEnabled() && StringUtils.hasText(ssoProperties.getIdpEndSessionUri())) {
            data.put("idpLogoutUrl", ssoProperties.getIdpEndSessionUri());
        }
        return HxResult.success("本地会话已销毁", data);
    }

    @Override
    public HxResult<?> validateSession(String authorizationHeader) {
        try {
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                return HxResult.unauthorized();
            }
            String token = authorizationHeader.substring(7);
            if (!StringUtils.hasText(token) || jwtUtil.isTokenExpired(token)) {
                return HxResult.unauthorized();
            }
            String username = jwtUtil.getSubject(token);
            User user = userService.getByUsername(username);
            if (user == null) {
                user = userService.getByPhone(username);
            }
            if (user == null) {
                return HxResult.unauthorized();
            }
            Map<String, Object> data = new HashMap<>();
            data.put("valid", true);
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("loginChannel", user.getLoginChannel());
            return HxResult.success(data);
        } catch (Exception e) {
            log.debug("会话校验失败: {}", e.getMessage());
            return HxResult.unauthorized();
        }
    }

    private void assertEnabled() {
        if (!ssoProperties.isEnabled()) {
            throw new SsoException("SSO_DISABLED", "SSO 功能未启用");
        }
    }

    private void assertOAuthConfigPresent() {
        if (!StringUtils.hasText(ssoProperties.getAuthorizationUri())) {
            throw new SsoException("SSO_CONFIG", "缺少 sso.authorization-uri");
        }
        if (!StringUtils.hasText(ssoProperties.getClientId())) {
            throw new SsoException("SSO_CONFIG", "缺少 sso.client-id");
        }
        if (!StringUtils.hasText(ssoProperties.getRedirectUri())) {
            throw new SsoException("SSO_CONFIG", "缺少 sso.redirect-uri");
        }
    }

    private ResolvedSsoTicket resolveRawJwt(HttpServletRequest request, SsoProperties.SsoMode mode) {
        if (mode == SsoProperties.SsoMode.TICKET_JWT) {
            String ticketParam = StringUtils.hasText(ssoProperties.getTicketParamName())
                    ? ssoProperties.getTicketParamName()
                    : SsoConstants.DEFAULT_TICKET_PARAM;
            String ticket = request.getParameter(ticketParam);
            if (!StringUtils.hasText(ticket)) {
                throw new SsoException("SSO_TICKET_MISSING", "缺少票据参数 " + ticketParam);
            }
            String returnUrlFromState = validateStateForTicketFlow(request);
            return ResolvedSsoTicket.builder()
                    .rawJwt(ticket)
                    .returnUrlFromState(returnUrlFromState)
                    .build();
        }
        if (mode != SsoProperties.SsoMode.OAUTH2_CODE) {
            throw new SsoException("SSO_MODE", "不支持的 SSO 模式: " + mode);
        }
        if (!StringUtils.hasText(ssoProperties.getTokenUri())) {
            throw new SsoException("SSO_CONFIG", "OAUTH2_CODE 模式缺少 sso.token-uri");
        }
        String state = request.getParameter(SsoConstants.PARAM_STATE);
        Payload payload = ssoStateStore.consume(state);
        String code = request.getParameter(SsoConstants.PARAM_CODE);
        Map<String, Object> tokenResponse = ssoOAuth2TokenClient.exchangeCodeForToken(code);
        String field = ssoProperties.getTokenToVerify() == SsoProperties.TokenToVerify.access_token
                ? "access_token"
                : "id_token";
        Object jwtObj = tokenResponse.get(field);
        if (!(jwtObj instanceof String) || !StringUtils.hasText((String) jwtObj)) {
            throw new SsoException("SSO_TOKEN_FIELD", "Token 响应缺少 " + field);
        }
        return ResolvedSsoTicket.builder()
                .rawJwt((String) jwtObj)
                .returnUrlFromState(payload == null ? null : payload.getReturnUrl())
                .build();
    }

    /**
     * @return state 中携带的 returnUrl（可能为空）
     */
    private String validateStateForTicketFlow(HttpServletRequest request) {
        String state = request.getParameter(SsoConstants.PARAM_STATE);
        if (!ssoProperties.isTicketRequireState()) {
            if (StringUtils.hasText(state)) {
                Payload p = ssoStateStore.consume(state);
                return p == null ? null : p.getReturnUrl();
            }
            return null;
        }
        if (!StringUtils.hasText(state)) {
            throw new SsoException("SSO_STATE_MISSING", "TICKET_JWT 模式缺少 state");
        }
        Payload p = ssoStateStore.consume(state);
        return p == null ? null : p.getReturnUrl();
    }

    private String resolveDisplayName(Claims claims) {
        String name = claims.get(SsoConstants.CLAIM_NAME, String.class);
        if (StringUtils.hasText(name)) {
            return name;
        }
        return claims.get(SsoConstants.CLAIM_PREFERRED_USERNAME, String.class);
    }

    private String resolvePostLoginRedirectBase(HttpServletRequest request, String returnUrlFromState) {
        if (StringUtils.hasText(returnUrlFromState)) {
            return returnUrlFromState;
        }
        String explicit = request.getParameter("returnUrl");
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        return ssoProperties.getFrontChannelSuccessRedirect();
    }

    private User bindOrProvisionUser(String sub, String email, String displayName) {
        String provider = ssoProperties.getProviderCode();
        ThirdPartyUser bound = findThirdParty(provider, sub);
        if (bound != null) {
            User user = userMapper.selectById(bound.getUserId());
            if (user == null || user.getDelFlag() != null && user.getDelFlag() == 1) {
                throw new SsoException("SSO_USER_MISSING", "绑定关系存在但本地用户不存在或已删除");
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new SsoException("SSO_USER_DISABLED", "本地用户已停用");
            }
            return user;
        }
        if (ssoProperties.isAutoBindByEmail() && StringUtils.hasText(email)) {
            User byEmail = userService.getByEmail(email);
            if (byEmail != null) {
                if (byEmail.getStatus() != null && byEmail.getStatus() == 0) {
                    throw new SsoException("SSO_USER_DISABLED", "本地用户已停用");
                }
                insertThirdParty(provider, sub, byEmail.getId(), displayName, email);
                return byEmail;
            }
        }
        if (!ssoProperties.isAutoRegister()) {
            throw new SsoException("SSO_NO_BINDING", "未找到绑定关系且未开启自动注册");
        }
        return autoRegisterUser(provider, sub, email, displayName);
    }

    private ThirdPartyUser findThirdParty(String provider, String sub) {
        QueryWrapper<ThirdPartyUser> w = new QueryWrapper<>();
        w.eq("provider", provider);
        w.eq("provider_user_id", sub);
        w.eq("del_flag", 0);
        return thirdPartyUserMapper.selectOne(w);
    }

    private void insertThirdParty(String provider, String sub, Long userId, String displayName, String email) {
        ThirdPartyUser row = new ThirdPartyUser();
        row.setUserId(userId);
        row.setProvider(provider);
        row.setProviderUserId(sub);
        row.setDisplayName(displayName);
        row.setEmailSnapshot(email);
        LocalDateTime now = LocalDateTime.now();
        row.setBindTime(now);
        row.setLastLoginAt(now);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDelFlag(0);
        thirdPartyUserMapper.insert(row);
    }

    private User autoRegisterUser(String provider, String sub, String email, String displayName) {
        User user = new User();
        user.setUsername(generateUniqueUsername(sub, email));
        user.setNickname(StringUtils.hasText(displayName) ? displayName : user.getUsername());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("SSO_RANDOM_" + UUID.randomUUID()));
        user.setStatus(1);
        user.setDelFlag(0);
        user.setGender(0);
        user.setUserType(ssoProperties.getDefaultUserType());
        user.setLoginChannel(LOGIN_CHANNEL_SSO);
        user.setSsoAutoRegistered(1);
        userMapper.insert(user);
        insertThirdParty(provider, sub, user.getId(), displayName, email);
        return user;
    }

    private String generateUniqueUsername(String sub, String email) {
        String base = buildUsernameBase(sub, email);
        String candidate = base;
        int i = 0;
        while (userService.getByUsername(candidate) != null && i < 20) {
            candidate = base + "_" + (100000 + (int) (Math.random() * 900000));
            i++;
        }
        if (userService.getByUsername(candidate) != null) {
            throw new SsoException("SSO_USERNAME_COLLISION", "无法生成唯一用户名");
        }
        return candidate;
    }

    private String buildUsernameBase(String sub, String email) {
        if (StringUtils.hasText(email) && email.contains("@")) {
            String local = sanitizeUsernamePart(email.substring(0, email.indexOf('@')));
            if (StringUtils.hasText(local)) {
                return local.length() > 48 ? local.substring(0, 48) : local;
            }
        }
        String sanitized = sanitizeUsernamePart(sub);
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "user";
        }
        String prefix = "sso_";
        String tail = sanitized.length() > 40 ? sanitized.substring(0, 40) : sanitized;
        return prefix + tail;
    }

    private String sanitizeUsernamePart(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
    }

    private void updateUserLoginMeta(User user, HttpServletRequest request) {
        User patch = new User();
        patch.setId(user.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        patch.setLastLoginIp(clientIp(request));
        userMapper.updateById(patch);
    }

    private void upsertThirdPartyBinding(Long userId, String sub, String email, String displayName) {
        String provider = ssoProperties.getProviderCode();
        ThirdPartyUser existing = findThirdParty(provider, sub);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            insertThirdParty(provider, sub, userId, displayName, email);
            return;
        }
        UpdateWrapper<ThirdPartyUser> uw = new UpdateWrapper<>();
        uw.eq("id", existing.getId());
        uw.set("last_login_at", now);
        uw.set("update_time", now);
        if (StringUtils.hasText(displayName)) {
            uw.set("display_name", displayName);
        }
        if (StringUtils.hasText(email)) {
            uw.set("email_snapshot", email);
        }
        thirdPartyUserMapper.update(null, uw);
    }

    private Map<String, Object> issueLocalTokens(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("userType", user.getUserType());
        String token = jwtUtil.generateToken(user.getUsername(), claims);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        User fresh = userMapper.selectById(user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("refreshToken", refreshToken);
        data.put("user", fresh);
        List<String> roleCodes = roleService.getLoginRoleCodes(fresh);
        data.put("roles", roleCodes);
        return data;
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
