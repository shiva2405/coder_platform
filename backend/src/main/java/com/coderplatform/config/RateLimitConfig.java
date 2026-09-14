package com.coderplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {

    private int anonymousPerHour = 20;
    private int authenticatedPerHour = 120;

    public int getAnonymousPerHour() {
        return anonymousPerHour;
    }

    public void setAnonymousPerHour(int anonymousPerHour) {
        this.anonymousPerHour = anonymousPerHour;
    }

    public int getAuthenticatedPerHour() {
        return authenticatedPerHour;
    }

    public void setAuthenticatedPerHour(int authenticatedPerHour) {
        this.authenticatedPerHour = authenticatedPerHour;
    }

    public int limitFor(boolean authenticated) {
        return authenticated ? authenticatedPerHour : anonymousPerHour;
    }
}
