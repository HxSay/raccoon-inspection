package com.raccoon.cloud.system.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("um_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String passwordHash;
    private String avatar;
    private Integer gender;
    private Integer userType;
    /**
     * 登录渠道：local=本地账号，sso=统一认证
     */
    private String loginChannel;
    /**
     * 是否由 SSO 自动注册：0 否，1 是
     */
    private Integer ssoAutoRegistered;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Integer failedLoginCount;
    private LocalDateTime lockoutAt;
    private Integer twoFactorEnabled;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private Integer delFlag;
}