package com.raccoon.cloud.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.model.dto.RegisterRequest;
import com.raccoon.cloud.system.model.dto.UserQueryRequest;
import com.raccoon.cloud.system.model.dto.UserRequest;

import java.util.List;

public interface UserService {
    /**
     * 用户登录
     */
    User login(String username, String password);

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 获取当前用户信息
     */
    User getCurrentUser();

    /**
     * 用户分页查询
     */
    IPage<User> page(UserQueryRequest query);

    /**
     * 用户新增
     */
    void add(UserRequest request);

    /**
     * 用户编辑
     */
    void update(UserRequest request);

    /**
     * 用户状态修改
     */
    void updateStatus(Long id, Integer status);

    /**
     * 重置密码
     */
    void resetPassword(Long id);

    /**
     * 删除用户
     */
    void delete(Long id);

    /**
     * 根据用户名查询用户
     */
    User getByUsername(String username);

    /**
     * 根据手机号查询用户
     */
    User getByPhone(String phone);

    /**
     * 根据邮箱查询用户（SSO 自动绑定时使用）
     */
    User getByEmail(String email);
}