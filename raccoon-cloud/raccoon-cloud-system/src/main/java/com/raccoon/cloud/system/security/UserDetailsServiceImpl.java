package com.raccoon.cloud.system.security;

import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 先尝试通过用户名查询
        User user = userService.getByUsername(username);
        if (user == null) {
            // 再尝试通过手机号查询
            user = userService.getByPhone(username);
        }
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        // 检查用户状态
        if (user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户已禁用");
        }
        // 检查是否已删除
        if (user.getDelFlag() == 1) {
            throw new UsernameNotFoundException("用户已删除");
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                new ArrayList<>()
        );
    }
}