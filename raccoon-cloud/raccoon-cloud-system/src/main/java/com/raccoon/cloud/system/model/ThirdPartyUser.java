package com.raccoon.cloud.system.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 第三方身份与本地用户的绑定关系。
 */
@Data
@TableName("um_third_party_user")
public class ThirdPartyUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** IdP 标识（如 issuer、租户编码），与配置 sso.provider-code 对应 */
    private String provider;

    /** IdP 用户唯一标识 sub */
    private String providerUserId;

    private String displayName;

    private String emailSnapshot;

    private LocalDateTime bindTime;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer delFlag;
}
