package com.raccoon.cloud.system.sso.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSO 配置项，与业务代码分离，支持 application-sso.yml / Nacos 覆盖。
 */
@Data
@ConfigurationProperties(prefix = "sso")
public class SsoProperties {

    /**
     * 是否启用 SSO 模块相关接口。
     */
    private boolean enabled = false;

    /**
     * 接入模式：OAUTH2_CODE（授权码 + 换票），TICKET_JWT（回调直接携带已签名的 JWT 票据）。
     */
    private SsoMode mode = SsoMode.OAUTH2_CODE;

    /**
     * 本系统在 IdP 侧注册的应用标识，同时写入第三方绑定表的 provider 字段。
     */
    private String providerCode = "default-idp";

    /** OAuth2 授权端点 */
    private String authorizationUri;

    /** OAuth2 Token 端点（授权码换 token） */
    private String tokenUri;

    /** OAuth2 客户端 ID */
    private String clientId;

    /** OAuth2 客户端密钥 */
    private String clientSecret;

    /**
     * 本系统回调地址，必须与 IdP 控制台配置完全一致。
     */
    private String redirectUri;

    /**
     * 授权请求 scope，OIDC 建议包含 openid。
     */
    private String scope = "openid profile email";

    /**
     * TICKET_JWT 模式下 URL 参数名。
     */
    private String ticketParamName = "ticket";

    /**
     * TICKET_JWT 模式下是否强制校验本系统下发的 state（推荐开启）。
     */
    private boolean ticketRequireState = true;

    /**
     * 换票成功后，浏览器重定向的前端地址（可选）。若为空则接口以 JSON 返回 token。
     */
    private String frontChannelSuccessRedirect;

    /**
     * IdP 单点登出地址（可选）。登出接口返回给前端用于跳转销毁 IdP 会话。
     */
    private String idpEndSessionUri;

    /**
     * 换票后优先校验 id_token；若 IdP 仅返回 JWT 形式的 access_token，可改为 access_token。
     */
    private TokenToVerify tokenToVerify = TokenToVerify.id_token;

    /**
     * 是否按邮箱自动关联已存在的本地账号（仅在首次 sub 未绑定时尝试）。
     */
    private boolean autoBindByEmail = true;

    /**
     * 是否在无绑定关系时自动注册本地用户并建立绑定。
     */
    private boolean autoRegister = true;

    /**
     * SSO 自动注册用户的默认 userType（与现有业务约定保持一致，默认 2）。
     */
    private Integer defaultUserType = 2;

    /** IdP JWT / 票据校验参数 */
    private IdpJwt idpJwt = new IdpJwt();

    /** State 在 Redis 中的 TTL（秒） */
    private long stateTtlSeconds = 600;

    /** jti 防重放记录 TTL 上限（秒），实际取 min(该值, JWT 剩余有效期) */
    private long replayTtlCapSeconds = 3600;

    public enum SsoMode {
        OAUTH2_CODE,
        TICKET_JWT
    }

    public enum TokenToVerify {
        id_token,
        access_token
    }

    @Data
    public static class IdpJwt {
        /**
         * 对称验签：HS256 / HS384 / HS512（与 IdP 约定一致）。
         * 与非对称二选一：配置了 publicKeyPem 则优先非对称。
         */
        private String hmacSecret;

        /**
         * RSA 公钥 PEM（-----BEGIN PUBLIC KEY----- ...），用于 RS256 验签。
         */
        private String publicKeyPem;

        /**
         * 期望的签发者 iss（可选，配置后强制校验）。
         */
        private String expectedIssuer;

        /**
         * 期望的受众 aud：可为字符串或逗号分隔多个值之一（可选）。
         */
        private String expectedAudience;
    }
}
