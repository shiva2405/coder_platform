package com.coderplatform.auth;

import com.coderplatform.config.AuthConfig;
import com.coderplatform.model.User;
import com.coderplatform.service.AuthService;
import com.coderplatform.util.ClientIpResolver;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthService authService;
    private final AuthConfig authConfig;

    public AuthHandshakeInterceptor(AuthService authService, AuthConfig authConfig) {
        this.authService = authService;
        this.authConfig = authConfig;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = queryToken(request);
        boolean explicitToken = token != null;
        if (token == null) {
            token = cookieToken(request);
        }
        if (token != null) {
            User user = authService.authenticate(token).orElse(null);
            if (user != null) {
                attributes.put(AuthContext.USER_ATTR, user);
                attributes.put(AuthContext.TOKEN_ATTR, token);
            } else if (explicitToken) {
                return false;
            }
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            attributes.put(AuthContext.CLIENT_IP_ATTR, ClientIpResolver.resolve(servletRequest.getServletRequest()));
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private String cookieToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return SessionCookies.read(servletRequest.getServletRequest(), authConfig.getCookieName());
        }
        return null;
    }

    private static String queryToken(ServerHttpRequest request) {
        List<String> values = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().get("token");
        if (values == null || values.isEmpty()) {
            return null;
        }
        String token = values.get(0);
        return token == null || token.isBlank() ? null : token;
    }
}
