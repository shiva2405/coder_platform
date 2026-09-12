package com.coderplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
@ConfigurationProperties(prefix = "admin")
public class AdminConfig {

    private String apiKey = "dev-admin-key";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean matches(String provided) {
        if (apiKey == null || apiKey.isBlank() || provided == null) {
            return false;
        }
        byte[] expected = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
