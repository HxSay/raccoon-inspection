package com.raccoon.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.raccoon.cloud.system.mapper.RoleMapper;
import com.raccoon.cloud.system.mapper.UserRoleRelMapper;
import com.raccoon.cloud.system.model.Role;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.model.UserRoleRel;
import com.raccoon.cloud.system.model.dto.RoleRequest;
import com.raccoon.cloud.system.service.RoleService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleRelMapper userRoleRelMapper;

    @Override
    public List<Role> list() {
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        wrapper.eq("del_flag", 0);
        wrapper.orderByAsc("sort");
        return roleMapper.selectList(wrapper);
    }

    @Override
    public void add(RoleRequest request) {
        Role role = new Role();
        BeanUtils.copyProperties(request, role);
        role.setDelFlag(0);
        role.setStatus(1);
        roleMapper.insert(role);
    }

    @Override
    public void update(RoleRequest request) {
        Role role = roleMapper.selectById(request.getId());
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }
        BeanUtils.copyProperties(request, role);
        roleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        // 先删除原有的角色关联
        QueryWrapper<UserRoleRel> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        userRoleRelMapper.delete(wrapper);

        // 新增角色关联
        for (Long roleId : roleIds) {
            UserRoleRel rel = new UserRoleRel();
            rel.setUserId(userId);
            rel.setRoleId(roleId);
            rel.setCreateTime(LocalDateTime.now());
            userRoleRelMapper.insert(rel);
        }
    }

    @Override
    public List<Role> getRolesByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        QueryWrapper<UserRoleRel> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        List<UserRoleRel> rels = userRoleRelMapper.selectList(wrapper);
        if (rels.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = rels.stream()
                .map(UserRoleRel::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Role> roles = new ArrayList<>();
        for (Long roleId : roleIds) {
            Role role = roleMapper.selectById(roleId);
            if (role != null && (role.getDelFlag() == null || role.getDelFlag() == 0)) {
                roles.add(role);
            }
        }
        return roles;
    }

    @Override
    public List<String> getLoginRoleCodes(User user) {
        if (user == null || user.getId() == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (Role r : getRolesByUserId(user.getId())) {
            if (StringUtils.hasText(r.getRoleCode())) {
                codes.add(r.getRoleCode().trim());
            }
        }
        if (codes.isEmpty() && isBuiltInPrivileged(user)) {
            codes.add("admin");
        }
        return new ArrayList<>(codes);
    }

    /**
     * 与前端 userType 约定一致：2 = B 端管理员；root 账号始终视为平台管理员。
     */
    private static boolean isBuiltInPrivileged(User u) {
        if (u.getUsername() != null && "root".equalsIgnoreCase(u.getUsername().trim())) {
            return true;
        }
        Integer t = u.getUserType();
        return t != null && t == 2;
    }
}