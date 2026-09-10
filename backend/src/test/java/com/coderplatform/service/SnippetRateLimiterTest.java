package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetRateLimiterTest {

    private SnippetRateLimiter limiter;

    @BeforeEach
    void setUp() {
        SnippetConfig config = new SnippetConfig();
        config.setRateLimitPerHour(20);
        limiter = new SnippetRateLimiter(config);
    }

    @Test
    void allowsTwentyCreatesPerIpThenBlocks() {
        long now = 1_700_000_000_000L;

        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4", now + i)).isTrue();
        }

        assertThat(limiter.tryAcquire("1.2.3.4", now + 21)).isFalse();
        assertThat(limiter.retryAfterSeconds("1.2.3.4", now + 21)).isPositive();
    }

    @Test
    void tracksIpsIndependently() {
        long now = 1_700_000_000_000L;
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1", now + i)).isTrue();
        }

        assertThat(limiter.tryAcquire("10.0.0.2", now + 21)).isTrue();
    }

    @Test
    void allowsAnotherCreateAfterWindowExpires() {
        long now = 1_700_000_000_000L;
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("8.8.8.8", now)).isTrue();
        }

        long afterWindow = now + Duration.ofHours(1).toMillis() + 1;
        assertThat(limiter.tryAcquire("8.8.8.8", afterWindow)).isTrue();
    }
}
