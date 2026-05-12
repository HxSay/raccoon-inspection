package com.raccoon.cloud.system.security;

import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.service.RoleService;
import com.raccoon.cloud.system.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.getByUsername(username);
        if (user == null) {
            user = userService.getByPhone(username);
        }
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        try {
            for (String code : roleService.getLoginRoleCodes(user)) {
                if (!StringUtils.hasText(code)) {
                    continue;
                }
                String c = code.trim();
                authorities.add(new SimpleGrantedAuthority(c.startsWith("ROLE_") ? c : "ROLE_" + c.toUpperCase().replace('-', '_')));
            }
        } catch (Exception e) {
            log.warn("加载用户角色失败 userId={}: {}", user.getId(), e.getMessage());
        }
        if (authorities.isEmpty() && isBuiltInPrivileged(user)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        // JWT 无状态接口不校验密码，但 User 构造器不允许 password 为 null
        String password = StringUtils.hasText(user.getPasswordHash()) ? user.getPasswordHash() : "{noop}N/A";

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                password,
                authorities
        );
    }

    /** 与 RoleServiceImpl 约定一致：2 = B 端管理员；root 为平台账号 */
    private static boolean isBuiltInPrivileged(User u) {
        if (u.getUsername() != null && "root".equalsIgnoreCase(u.getUsername().trim())) {
            return true;
        }
        Integer t = u.getUserType();
        return t != null && t == 2;
    }
}
