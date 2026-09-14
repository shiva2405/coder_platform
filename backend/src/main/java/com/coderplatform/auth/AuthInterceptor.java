package com.coderplatform.auth;

import com.coderplatform.config.AuthConfig;
import com.coderplatform.model.User;
import com.coderplatform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTHORIZATION = "Authorization";

    private final AuthService authService;
    private final AuthConfig authConfig;

    public AuthInterceptor(AuthService authService, AuthConfig authConfig) {
        this.authService = authService;
        this.authConfig = authConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = extractToken(request);
        if (token != null) {
            User user = authService.authenticate(token).orElse(null);
            if (user != null) {
                AuthContext.set(user, token);
                request.setAttribute(AuthContext.USER_ATTR, user);
                request.setAttribute(AuthContext.TOKEN_ATTR, token);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        AuthContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String bearer = header.substring(7).trim();
            if (!bearer.isEmpty()) {
                return bearer;
            }
        }
        return SessionCookies.read(request, authConfig.getCookieName());
    }
}
