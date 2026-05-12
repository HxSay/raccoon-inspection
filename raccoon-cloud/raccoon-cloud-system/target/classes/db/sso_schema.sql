-- SSO 相关表结构变更（在现有库 hxsay-agent-sys 上执行）
-- 1) 现有用户表 um_user 补充字段

ALTER TABLE um_user
    ADD COLUMN login_channel VARCHAR(32) NULL DEFAULT 'local' COMMENT '登录渠道：local=本地账号，sso=统一认证' AFTER user_type,
    ADD COLUMN sso_auto_registered TINYINT NOT NULL DEFAULT 0 COMMENT '是否由SSO自动注册：0否 1是' AFTER login_channel;

-- 若列已存在会报错，可按环境拆分执行或改为存储过程判断 INFORMATION_SCHEMA

-- 2) 第三方身份与本地用户关联表

CREATE TABLE IF NOT EXISTS um_third_party_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '关联 um_user.id',
    provider VARCHAR(64) NOT NULL COMMENT 'IdP 标识（如 issuer、租户编码）',
    provider_user_id VARCHAR(256) NOT NULL COMMENT 'IdP 用户唯一标识 sub',
    display_name VARCHAR(128) NULL COMMENT 'IdP 展示名快照',
    email_snapshot VARCHAR(128) NULL COMMENT '绑定时的邮箱快照（审计用）',
    bind_time DATETIME NOT NULL COMMENT '首次绑定时间',
    last_login_at DATETIME NULL COMMENT '最近一次通过该 IdP 登录时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_subject (provider, provider_user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 第三方用户与本地用户绑定关系';

-- 外键可选：若需强约束可取消注释（确保 um_user 为 InnoDB 且类型一致）
-- ALTER TABLE um_third_party_user ADD CONSTRAINT fk_tpu_um_user FOREIGN KEY (user_id) REFERENCES um_user (id);
