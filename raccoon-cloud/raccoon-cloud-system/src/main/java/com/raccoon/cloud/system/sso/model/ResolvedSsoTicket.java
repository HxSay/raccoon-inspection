package com.raccoon.cloud.system.sso.model;

import lombok.Builder;
import lombok.Data;

/**
 * 回调阶段解析得到的 IdP JWT 原文，以及可选的 state 中携带的前端回跳地址。
 */
@Data
@Builder
public class ResolvedSsoTicket {

    private String rawJwt;

    /**
     * 来自一次性 state 的 returnUrl（优先于回调 URL 上的自定义参数）。
     */
    private String returnUrlFromState;
}
