package com.agent.tenant.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// 租户数据隔离拦截器: 从请求头提取 X-Tenant-Id 并设置到 ThreadLocal
public class TenantInterceptor implements HandlerInterceptor {
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    // 2. 请求进入Controller之前执行
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从请求头获取租户ID：前端每次请求必须带 X-Tenant-Id
        String tenantId = request.getHeader("X-Tenant-Id");
        // 如果有租户ID，就存入 ThreadLocal
        if (tenantId != null) CURRENT_TENANT.set(Long.parseLong(tenantId));
        return true;
    }

    // 3. 请求完全结束后执行（页面渲染完）
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理！防止内存泄漏 + 线程复用导致串数据
        CURRENT_TENANT.remove();
    }

    // 4. 静态工具方法：任何地方随时获取当前租户ID
    public static Long getCurrentTenantId() {

        return CURRENT_TENANT.get();
    }
}
