package com.agent.auth.controller;

import com.agent.auth.dto.LoginRequest;
import com.agent.auth.dto.LoginResponse;
import com.agent.auth.service.AuthService;
import com.agent.common.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }
}
