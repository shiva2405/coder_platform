package com.coderplatform.service;

import com.coderplatform.config.RateLimitConfig;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.model.User;
import org.springframework.stereotype.Service;

@Service
public class ExecutionQuotaService {

    private final SnippetRateLimiter rateLimiter;
    private final RateLimitConfig rateLimitConfig;

    public ExecutionQuotaService(SnippetRateLimiter rateLimiter, RateLimitConfig rateLimitConfig) {
        this.rateLimiter = rateLimiter;
        this.rateLimitConfig = rateLimitConfig;
    }

    public void consume(User user, String ip) {
        if (user != null && user.isAdmin()) {
            return;
        }
        String key = user != null ? "exec:user:" + user.getId() : "exec:ip:" + safeIp(ip);
        int limit = rateLimitConfig.limitFor(user != null);
        if (!rateLimiter.tryAcquire(key, limit)) {
            throw new RateLimitExceededException(
                    "Executions are limited to " + limit + " per hour"
                            + (user != null ? "." : " for anonymous users. Sign in for a higher limit."),
                    rateLimiter.retryAfterSeconds(key)
            );
        }
    }

    private static String safeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}
