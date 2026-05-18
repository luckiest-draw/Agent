package com.agent.auth.controller;

import com.agent.auth.dto.LoginRequest;
import com.agent.auth.dto.LoginResponse;
import com.agent.auth.dto.RegisterRequest;
import com.agent.auth.service.AuthService;
import com.agent.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {

        return Result.ok(authService.login(req));
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }
}
