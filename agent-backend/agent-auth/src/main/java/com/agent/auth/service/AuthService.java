package com.agent.auth.service;

import com.agent.auth.dto.LoginRequest;
import com.agent.auth.dto.LoginResponse;
import com.agent.auth.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest req);
    LoginResponse register(RegisterRequest req);
}
