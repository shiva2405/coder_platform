package com.coderplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "snippet")
public class SnippetConfig {

    private int maxCodeBytes = 262144;
    private int maxTitleLength = 200;
    private int rateLimitPerHour = 20;
    private int slugLength = 8;

    public int getMaxCodeBytes() {
        return maxCodeBytes;
    }

    public void setMaxCodeBytes(int maxCodeBytes) {
        this.maxCodeBytes = maxCodeBytes;
    }

    public int getMaxTitleLength() {
        return maxTitleLength;
    }

    public void setMaxTitleLength(int maxTitleLength) {
        this.maxTitleLength = maxTitleLength;
    }

    public int getRateLimitPerHour() {
        return rateLimitPerHour;
    }

    public void setRateLimitPerHour(int rateLimitPerHour) {
        this.rateLimitPerHour = rateLimitPerHour;
    }

    public int getSlugLength() {
        return slugLength;
    }

    public void setSlugLength(int slugLength) {
        this.slugLength = slugLength;
    }
}
