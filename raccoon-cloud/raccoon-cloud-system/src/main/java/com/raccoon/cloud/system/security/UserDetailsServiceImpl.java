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
        for (String code : roleService.getLoginRoleCodes(user)) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String c = code.trim();
            authorities.add(new SimpleGrantedAuthority(c.startsWith("ROLE_") ? c : "ROLE_" + c.toUpperCase().replace('-', '_')));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                authorities
        );
    }
}
