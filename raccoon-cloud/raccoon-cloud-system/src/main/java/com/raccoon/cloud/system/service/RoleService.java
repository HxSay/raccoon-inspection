package com.raccoon.cloud.system.service;

import com.raccoon.cloud.system.model.Role;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.model.dto.RoleRequest;

import java.util.List;

public interface RoleService {
    /**
     * 获取角色列表
     */
    List<Role> list();

    /**
     * 角色新增
     */
    void add(RoleRequest request);

    /**
     * 角色编辑
     */
    void update(RoleRequest request);

    /**
     * 给用户分配角色
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 根据用户ID获取角色列表
     */
    List<Role> getRolesByUserId(Long userId);

    /**
     * 登录/鉴权用：库中角色编码 + B 端管理员 / root 兜底 admin
     */
    List<String> getLoginRoleCodes(User user);
}