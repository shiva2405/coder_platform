package com.coderplatform.config;

import com.coderplatform.auth.AuthContext;
import com.coderplatform.exception.UnauthorizedAdminException;
import com.coderplatform.model.User;
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
        if (adminConfig.matches(request.getHeader(HEADER))) {
            return true;
        }
        User user = AuthContext.getUser();
        if (user != null && user.isAdmin()) {
            return true;
        }
        throw new UnauthorizedAdminException();
    }
}
