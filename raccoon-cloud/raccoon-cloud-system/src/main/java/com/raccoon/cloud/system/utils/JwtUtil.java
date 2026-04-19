package com.raccoon.cloud.system.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire}")
    private long expire;

    @Value("${jwt.refreshExpire}")
    private long refreshExpire;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成token
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expire * 1000);

        // 确保claims中包含subject
        if (claims == null) {
            claims = new HashMap<>();
        }
        claims.put("sub", subject);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成刷新token
     */
    public String generateRefreshToken(String subject) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + refreshExpire * 1000);

        // 创建包含subject的claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析token
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.error("Token已过期: {}", e.getMessage());
            throw e;
        } catch (SignatureException e) {
            log.error("Token签名错误: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Token解析错误: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 从token中获取subject
     */
    public String getSubject(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 从token中获取claims
     */
    public Map<String, Object> getClaims(String token) {
        return parseToken(token);
    }

    /**
     * 验证token是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
