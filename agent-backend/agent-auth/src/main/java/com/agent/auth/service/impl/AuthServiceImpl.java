package com.agent.auth.service.impl;

import com.agent.auth.service.AuthService;

import com.agent.auth.dto.LoginRequest;
import com.agent.auth.dto.LoginResponse;
import com.agent.auth.dto.RegisterRequest;
import com.agent.auth.entity.Role;
import com.agent.auth.entity.User;
import com.agent.auth.mapper.RoleMapper;
import com.agent.auth.mapper.UserMapper;
import com.agent.auth.security.JwtUtil;
import com.agent.common.BusinessException;
import com.agent.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest req) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        if (!user.getEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        List<String> roles = userMapper.selectRolesByUserId(user.getId())
            .stream().map(Role::getName).toList();
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roles);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUser(Map.of("id", user.getId(), "username", user.getUsername(), "roles", roles));
        return resp;
    }

    @Override
    public LoginResponse register(RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw new BusinessException(ErrorCode.USERNAME_BLANK);
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT);
        }
        boolean exists = userMapper.exists(
            new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (exists) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        Role userRole = roleMapper.selectOne(
            new LambdaQueryWrapper<Role>().eq(Role::getName, "USER"));
        if (userRole == null) {
            userRole = new Role();
            userRole.setName("USER");
            userRole.setDescription("普通用户");
            roleMapper.insert(userRole);
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail());
        user.setEnabled(true);
        userMapper.insert(user);

        userMapper.insertUserRole(user.getId(), userRole.getId());

        List<String> roles = List.of("USER");
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roles);
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUser(Map.of("id", user.getId(), "username", user.getUsername(), "roles", roles));
        return resp;
    }
}
