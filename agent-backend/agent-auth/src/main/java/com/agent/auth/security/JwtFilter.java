package com.agent.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        // 1. 判断Token不为空 + 校验Token有效
        if (token != null && jwtUtil.validateToken(token)) {
            // 2. 解析Token，拿到用户信息载体
            var claims = jwtUtil.parseToken(token);
            // 3. 从Claims中取出之前存入的用户信息
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);
            // 4. 取出角色列表（需要忽略泛型警告）
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            // 5. 把角色转成Spring Security需要的权限格式
            var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
            // 6. 创建认证对象（已认证状态）
            var auth = new UsernamePasswordAuthenticationToken(userId, username, authorities);
            // 7. 把认证信息存入Spring安全上下文 = 登录成功
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
