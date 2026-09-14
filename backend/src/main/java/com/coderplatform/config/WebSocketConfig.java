package com.coderplatform.config;

import com.coderplatform.auth.AuthHandshakeInterceptor;
import com.coderplatform.websocket.ExecutionWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ExecutionWebSocketHandler executionWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final String allowedOrigins;

    public WebSocketConfig(
            ExecutionWebSocketHandler executionWebSocketHandler,
            AuthHandshakeInterceptor authHandshakeInterceptor,
            @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String allowedOrigins
    ) {
        this.executionWebSocketHandler = executionWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        registry.addHandler(executionWebSocketHandler, "/ws/execute")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins(origins);
    }
}
