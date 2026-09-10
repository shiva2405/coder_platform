package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class SnippetRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> hits = new ConcurrentHashMap<>();
    private final SnippetConfig config;

    public SnippetRateLimiter(SnippetConfig config) {
        this.config = config;
    }

    public boolean tryAcquire(String ip) {
        return tryAcquire(ip, System.currentTimeMillis());
    }

    boolean tryAcquire(String ip, long nowMillis) {
        long cutoff = nowMillis - WINDOW.toMillis();
        ConcurrentLinkedDeque<Long> times = hits.computeIfAbsent(ip, key -> new ConcurrentLinkedDeque<>());
        synchronized (times) {
            prune(times, cutoff);
            if (times.size() >= config.getRateLimitPerHour()) {
                return false;
            }
            times.addLast(nowMillis);
            return true;
        }
    }

    public long retryAfterSeconds(String ip) {
        return retryAfterSeconds(ip, System.currentTimeMillis());
    }

    long retryAfterSeconds(String ip, long nowMillis) {
        ConcurrentLinkedDeque<Long> times = hits.get(ip);
        if (times == null || times.isEmpty()) {
            return 0;
        }
        synchronized (times) {
            Long oldest = times.peekFirst();
            if (oldest == null) {
                return 0;
            }
            long retryAt = oldest + WINDOW.toMillis();
            return Math.max(1, (retryAt - nowMillis + 999) / 1000);
        }
    }

    private void prune(ConcurrentLinkedDeque<Long> times, long cutoff) {
        while (!times.isEmpty()) {
            Long first = times.peekFirst();
            if (first == null || first >= cutoff) {
                break;
            }
            times.pollFirst();
        }
    }
}
