package com.raccoon.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.raccoon.cloud.system.mapper.RoleMapper;
import com.raccoon.cloud.system.mapper.UserRoleRelMapper;
import com.raccoon.cloud.system.model.Role;
import com.raccoon.cloud.system.model.UserRoleRel;
import com.raccoon.cloud.system.model.dto.RoleRequest;
import com.raccoon.cloud.system.service.RoleService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
        // 这里需要实现根据用户ID获取角色列表的逻辑
        // 可以使用MyBatis-Plus的关联查询或自定义SQL
        return null;
    }
}