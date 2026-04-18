package com.raccoon.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.mapper.UserMapper;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.model.dto.UserQueryRequest;
import com.raccoon.cloud.system.model.dto.UserRequest;
import com.raccoon.cloud.system.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public User login(String username, String password) {
        // 先尝试通过用户名查询
        User user = getByUsername(username);
        if (user == null) {
            // 再尝试通过手机号查询
            user = getByPhone(username);
        }
        if (user == null) {
            return null;
        }
        // 验证密码
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return null;
        }
        // 检查用户状态
        if (user.getStatus() == 0) {
            return null;
        }
        return user;
    }

    @Override
    public User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return getByUsername(username);
    }

    @Override
    public IPage<User> page(UserQueryRequest query) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (query.getUsername() != null) {
            wrapper.like("username", query.getUsername());
        }
        if (query.getPhone() != null) {
            wrapper.like("phone", query.getPhone());
        }
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus());
        }
        wrapper.eq("del_flag", 0);
        wrapper.orderByDesc("create_time");

        Page<User> page = new Page<>(query.getPage(), query.getSize());
        return userMapper.selectPage(page, wrapper);
    }

    @Override
    public void add(UserRequest request) {
        User user = new User();
        BeanUtils.copyProperties(request, user);
        // 加密密码
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        // 设置默认值
        user.setStatus(1);
        user.setDelFlag(0);
        user.setGender(0);
        user.setUserType(2);
        userMapper.insert(user);
    }

    @Override
    public void update(UserRequest request) {
        User user = userMapper.selectById(request.getId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        BeanUtils.copyProperties(request, user);
        // 如果修改了密码，重新加密
        if (request.getPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        userMapper.updateById(user);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 重置密码为123456
        user.setPasswordHash(passwordEncoder.encode("123456"));
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 软删除
        user.setDelFlag(1);
        userMapper.updateById(user);
    }

    @Override
    public User getByUsername(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        wrapper.eq("del_flag", 0);
        return userMapper.selectOne(wrapper);
    }

    @Override
    public User getByPhone(String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone);
        wrapper.eq("del_flag", 0);
        return userMapper.selectOne(wrapper);
    }
}