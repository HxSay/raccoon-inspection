package com.raccoon.cloud.system.sso.util;

import com.raccoon.cloud.system.sso.config.SsoProperties;
import com.raccoon.cloud.system.sso.exception.SsoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * OAuth2 授权码换 token（标准 application/x-www-form-urlencoded）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoOAuth2TokenClient {

    private final RestTemplate ssoRestTemplate;
    private final SsoProperties ssoProperties;

    @SuppressWarnings("unchecked")
    public Map<String, Object> exchangeCodeForToken(String code) {
        if (code == null || code.isBlank()) {
            throw new SsoException("SSO_CODE_MISSING", "缺少授权码 code");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", ssoProperties.getRedirectUri());
        form.add("client_id", ssoProperties.getClientId());
        if (StringUtils.hasText(ssoProperties.getClientSecret())) {
            form.add("client_secret", ssoProperties.getClientSecret());
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<Map> resp = ssoRestTemplate.postForEntity(ssoProperties.getTokenUri(), entity, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                throw new SsoException("SSO_TOKEN_EMPTY", "Token 端点返回空响应");
            }
            return body;
        } catch (HttpStatusCodeException e) {
            log.warn("换票失败 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new SsoException("SSO_TOKEN_EXCHANGE", "授权码换票失败: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new SsoException("SSO_TOKEN_EXCHANGE", "授权码换票异常", e);
        }
    }
}
