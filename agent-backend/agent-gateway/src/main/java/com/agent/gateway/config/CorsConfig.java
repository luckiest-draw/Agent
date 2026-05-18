package com.agent.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        // 1. 创建跨域配置对象
        CorsConfiguration config = new CorsConfiguration();
        // 2. 允许所有来源（域名）访问
        config.setAllowedOriginPatterns(Arrays.asList("*"));
        // 3. 允许的请求方法
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 4. 允许所有请求头
        config.setAllowedHeaders(Arrays.asList("*"));
        // 5. 允许携带 Cookie / Token 凭证
        config.setAllowCredentials(true);
        // 6. 预检请求缓存时间（秒）
        config.setMaxAge(3600L);
        // 7. 注册到所有接口 /**
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
