package com.raccoon.cloud.system.service.impl;

import com.raccoon.cloud.system.model.LoginLog;
import com.raccoon.cloud.system.model.User;
import com.raccoon.cloud.system.model.dto.LoginRequest;
import com.raccoon.cloud.system.model.dto.RegisterRequest;
import com.raccoon.cloud.system.mapper.LoginLogMapper;
import com.raccoon.cloud.system.service.AuthService;
import com.raccoon.cloud.system.service.UserService;
import com.raccoon.cloud.system.utils.JwtUtil;
import com.raccoon.common.result.HxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
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

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public HxResult<?> login(LoginRequest loginRequest) {
        try {
            // 进行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 记录登录日志
            recordLoginLog(loginRequest.getUsername(), 1, "登录成功");

            // 获取用户信息
            User user = userService.getCurrentUser();

            // 生成token
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId());
            claims.put("username", user.getUsername());
            claims.put("userType", user.getUserType());

            String token = jwtUtil.generateToken(user.getUsername(), claims);
            String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("refreshToken", refreshToken);
            data.put("user", user);

            return HxResult.success(data);
        } catch (Exception e) {
            // 记录登录失败日志
            recordLoginLog(loginRequest.getUsername(), 0, "登录失败: " + e.getMessage());
            return HxResult.fail("用户名或密码错误");
        }
    }

    @Override
    public HxResult<?> refreshToken() {
        try {
            // 从请求头获取token
            String token = request.getHeader("Authorization");
            if (token == null || !token.startsWith("Bearer ")) {
                return HxResult.fail("Token不存在");
            }

            token = token.substring(7);
            String username = jwtUtil.getSubject(token);

            // 获取用户信息
            User user = userService.getByUsername(username);
            if (user == null) {
                return HxResult.fail("用户不存在");
            }

            // 生成新token
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId());
            claims.put("username", user.getUsername());
            claims.put("userType", user.getUserType());

            String newToken = jwtUtil.generateToken(user.getUsername(), claims);
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getUsername());

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("token", newToken);
            data.put("refreshToken", newRefreshToken);

            return HxResult.success(data);
        } catch (Exception e) {
            log.error("刷新Token失败: {}", e.getMessage());
            return HxResult.fail("刷新Token失败");
        }
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