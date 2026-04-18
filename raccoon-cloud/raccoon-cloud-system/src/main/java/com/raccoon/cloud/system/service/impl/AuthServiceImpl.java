package com.raccoon.cloud.system.service.impl;

import com.raccoon.cloud.system.model.LoginLog;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.model.dto.LoginRequest;
import com.raccoon.cloud.system.model.dto.RegisterRequest;
import com.raccoon.cloud.system.mapper.LoginLogMapper;
import com.raccoon.cloud.system.service.AuthService;
import com.raccoon.cloud.system.service.UserService;
import com.raccoon.common.result.HxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private HttpServletRequest request;

    @Override
    public HxResult<?> login(LoginRequest request) {
        try {
            // 进行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 记录登录日志
            recordLoginLog(request.getUsername(), 1, "登录成功");

            // 返回用户信息
            User user = userService.getCurrentUser();
            return HxResult.success(user);
        } catch (Exception e) {
            // 记录登录失败日志
            recordLoginLog(request.getUsername(), 0, "登录失败: " + e.getMessage());
            return HxResult.fail("用户名或密码错误");
        }
    }

    @Override
    public HxResult<?> refreshToken() {
        // 实现Token刷新逻辑
        return HxResult.success("Token刷新成功");
    }

    @Override
    public HxResult<?> logout() {
        SecurityContextHolder.clearContext();
        return HxResult.success("退出登录成功");
    }

    @Override
    public HxResult<?> getCurrentUserInfo() {
        User user = userService.getCurrentUser();
        return HxResult.success(user);
    }

    @Override
    public HxResult<?> register(RegisterRequest request) {
        try {
            // 检查密码是否一致
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return HxResult.fail("两次输入的密码不一致");
            }

            // 检查是否同意隐私协议
            if (!request.isAgree()) {
                return HxResult.fail("请同意隐私协议");
            }

            // 调用UserService进行注册
            userService.register(request);

            // 记录注册日志
            recordLoginLog(request.getUsername(), 1, "注册成功");

            return HxResult.success("注册成功");
        } catch (Exception e) {
            // 记录注册失败日志
            recordLoginLog(request.getUsername(), 0, "注册失败: " + e.getMessage());
            return HxResult.fail("注册失败: " + e.getMessage());
        }
    }

    /**
     * 记录登录日志
     */
    private void recordLoginLog(String username, int status, String message) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setIp(getClientIp());
        log.setStatus(status);
        log.setMessage(message);
        log.setCreateTime(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}