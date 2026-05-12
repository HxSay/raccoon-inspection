package com.raccoon.cloud.system.sso.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raccoon.cloud.system.sso.config.SsoProperties;
import com.raccoon.cloud.system.sso.constant.SsoConstants;
import com.raccoon.cloud.system.sso.exception.SsoException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * OAuth2 state 一次性存储，绑定可选的前端回跳地址，防止 CSRF。
 */
@Component
@RequiredArgsConstructor
public class SsoStateStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final SsoProperties ssoProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String issueState(String returnUrl) {
        String state = UUID.randomUUID().toString().replace("-", "");
        Payload payload = new Payload();
        payload.setReturnUrl(returnUrl);
        String key = SsoConstants.REDIS_STATE_PREFIX + state;
        try {
            String json = objectMapper.writeValueAsString(payload);
            long ttl = Math.max(60, ssoProperties.getStateTtlSeconds());
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttl));
            return state;
        } catch (JsonProcessingException e) {
            throw new SsoException("SSO_STATE_SERIALIZE", "state 序列化失败", e);
        } catch (DataAccessException e) {
            throw new SsoException("SSO_REDIS", "Redis 不可用，无法保存 OAuth2 state", e);
        }
    }

    public Payload consume(String state) {
        if (state == null || state.isBlank()) {
            throw new SsoException("SSO_STATE_MISSING", "缺少 state 参数");
        }
        String key = SsoConstants.REDIS_STATE_PREFIX + state;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                throw new SsoException("SSO_STATE_INVALID", "state 无效或已过期");
            }
            stringRedisTemplate.delete(key);
            return objectMapper.readValue(json, Payload.class);
        } catch (JsonProcessingException e) {
            throw new SsoException("SSO_STATE_PARSE", "state 解析失败", e);
        } catch (DataAccessException e) {
            throw new SsoException("SSO_REDIS", "Redis 不可用，无法校验 OAuth2 state", e);
        }
    }

    @Data
    public static class Payload {
        /** 登录成功后前端可回跳的地址（可选） */
        private String returnUrl;
    }
}
