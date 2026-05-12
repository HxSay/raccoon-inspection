package com.raccoon.cloud.system.sso.util;

import com.raccoon.cloud.system.sso.config.SsoProperties;
import com.raccoon.cloud.system.sso.exception.SsoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 校验 IdP 签发的 JWT（id_token / access_token / ticket），支持 HS 对称与 RSA 公钥验签，
 * 并可选校验 iss / aud，防止跨租户与错发令牌。
 */
@Slf4j
@Component
public class IdpJwtVerifier {

    public Claims verifyAndParseClaims(String jwt, SsoProperties props) {
        if (!StringUtils.hasText(jwt)) {
            throw new SsoException("SSO_TICKET_MISSING", "JWT 票据为空");
        }
        try {
            var parser = Jwts.parserBuilder()
                    .setSigningKey(resolveSigningKey(props))
                    .build();
            Claims claims = parser.parseClaimsJws(jwt).getBody();
            validateTemporal(claims);
            validateIssuerAudience(claims, props);
            return claims;
        } catch (SsoException e) {
            throw e;
        } catch (Exception e) {
            log.warn("IdP JWT 校验失败: {}", e.getMessage());
            throw new SsoException("SSO_TICKET_INVALID", "JWT 票据非法或验签失败", e);
        }
    }

    private void validateTemporal(Claims claims) {
        Date exp = claims.getExpiration();
        if (exp != null && exp.before(new Date())) {
            throw new SsoException("SSO_TICKET_EXPIRED", "JWT 已过期");
        }
    }

    private void validateIssuerAudience(Claims claims, SsoProperties props) {
        SsoProperties.IdpJwt idp = props.getIdpJwt();
        if (StringUtils.hasText(idp.getExpectedIssuer())) {
            String iss = claims.getIssuer();
            if (!idp.getExpectedIssuer().equals(iss)) {
                throw new SsoException("SSO_ISS_MISMATCH", "JWT iss 与期望不一致");
            }
        }
        if (StringUtils.hasText(idp.getExpectedAudience())) {
            Object audClaim = claims.get("aud");
            List<String> expected = Arrays.stream(idp.getExpectedAudience().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            boolean ok = false;
            if (audClaim instanceof String) {
                ok = expected.contains(audClaim);
            } else if (audClaim instanceof List<?> list) {
                ok = list.stream().anyMatch(a -> expected.contains(String.valueOf(a)));
            }
            if (!ok) {
                throw new SsoException("SSO_AUD_MISMATCH", "JWT aud 与期望不一致");
            }
        }
    }

    private Object resolveSigningKey(SsoProperties props) {
        SsoProperties.IdpJwt idp = props.getIdpJwt();
        if (StringUtils.hasText(idp.getPublicKeyPem())) {
            return parseRsaPublicKey(idp.getPublicKeyPem());
        }
        if (StringUtils.hasText(idp.getHmacSecret())) {
            SecretKey key = Keys.hmacShaKeyFor(idp.getHmacSecret().getBytes(StandardCharsets.UTF_8));
            return key;
        }
        throw new SsoException("SSO_IDP_KEY", "请在 sso.idp-jwt 下配置 hmac-secret 或 public-key-pem");
    }

    private PublicKey parseRsaPublicKey(String pem) {
        try {
            String normalized = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Decoders.BASE64.decode(normalized);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new SsoException("SSO_IDP_KEY", "RSA 公钥 PEM 解析失败", e);
        }
    }
}
