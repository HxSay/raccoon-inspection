package com.raccoon.cloud.system.sso.constant;

/**
 * SSO 模块常量：Redis 键前缀、请求参数名、标准 Claim 名等。
 */
public final class SsoConstants {

    private SsoConstants() {
    }

    /** OAuth2 / OIDC 授权端点常用参数 */
    public static final String PARAM_RESPONSE_TYPE = "response_type";
    public static final String PARAM_CLIENT_ID = "client_id";
    public static final String PARAM_REDIRECT_URI = "redirect_uri";
    public static final String PARAM_SCOPE = "scope";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_CODE = "code";
    public static final String PARAM_ERROR = "error";
    public static final String PARAM_ERROR_DESCRIPTION = "error_description";

    /** 本模块 TICKET_JWT 模式下的票据参数名（可配置覆盖默认值） */
    public static final String DEFAULT_TICKET_PARAM = "ticket";

    /** Redis：一次性 state，防 CSRF */
    public static final String REDIS_STATE_PREFIX = "sso:state:";

    /** Redis：jti 消费标记，防重放 */
    public static final String REDIS_JTI_PREFIX = "sso:jti:";

    /** OIDC / JWT 常见 Claims */
    public static final String CLAIM_SUB = "sub";
    public static final String CLAIM_JTI = "jti";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    public static final String CLAIM_ISS = "iss";
    public static final String CLAIM_AUD = "aud";
}
