package com.raccoon.cloud.system.sso.util;

import com.raccoon.cloud.system.sso.config.SsoProperties;
import com.raccoon.cloud.system.sso.constant.SsoConstants;
import com.raccoon.cloud.system.sso.exception.SsoException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 基于 Redis 的 jti（或整票摘要）一次性消费，防止票据重放。
 */
@Component
@RequiredArgsConstructor
public class SsoReplayGuard {

    private final StringRedisTemplate stringRedisTemplate;
    private final SsoProperties ssoProperties;

    /**
     * 标记票据已使用；若已存在则抛出异常。
     *
     * @param claims 已验签通过的 Claims
     * @param rawJwt 原始 JWT 串（在缺少 jti 时用于生成指纹）
     */
    public void assertAndConsumeTicket(Claims claims, String rawJwt) {
        long ttlSeconds = computeTtlSeconds(claims);
        String key = buildRedisKey(claims, rawJwt);
        try {
            Boolean first = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
            if (!Boolean.TRUE.equals(first)) {
                throw new SsoException("SSO_REPLAY", "检测到重放攻击或票据已被使用");
            }
        } catch (DataAccessException e) {
            throw new SsoException("SSO_REDIS", "Redis 不可用，无法完成重放防护", e);
        }
    }

    private long computeTtlSeconds(Claims claims) {
        Date exp = claims.getExpiration();
        long cap = Math.max(60, ssoProperties.getReplayTtlCapSeconds());
        if (exp == null) {
            return cap;
        }
        long remain = exp.getTime() - System.currentTimeMillis();
        if (remain <= 0) {
            return 1;
        }
        return Math.min(cap, remain / 1000);
    }

    private String buildRedisKey(Claims claims, String rawJwt) {
        String jti = claims.get(SsoConstants.CLAIM_JTI, String.class);
        if (StringUtils.hasText(jti)) {
            return SsoConstants.REDIS_JTI_PREFIX + jti;
        }
        String fingerprint = sha256Hex(rawJwt);
        return SsoConstants.REDIS_JTI_PREFIX + "fp:" + fingerprint;
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new SsoException("SSO_INTERNAL", "摘要计算失败", e);
        }
    }
}
