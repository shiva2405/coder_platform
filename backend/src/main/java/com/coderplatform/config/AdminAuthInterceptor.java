package com.coderplatform.config;

import com.coderplatform.exception.UnauthorizedAdminException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Key";

    private final AdminConfig adminConfig;

    public AdminAuthInterceptor(AdminConfig adminConfig) {
        this.adminConfig = adminConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!adminConfig.matches(request.getHeader(HEADER))) {
            throw new UnauthorizedAdminException();
        }
        return true;
    }
}
