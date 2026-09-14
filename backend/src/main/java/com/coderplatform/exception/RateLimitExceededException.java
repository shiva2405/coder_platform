package com.coderplatform.exception;

public class RateLimitExceededException extends RuntimeException {

    public static final String RATE_LIMIT = "RATE_LIMIT";

    private final long retryAfterSeconds;
    private final String reason;

    public RateLimitExceededException(long retryAfterSeconds) {
        this("Rate limit exceeded. Try again later.", retryAfterSeconds);
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        this(message, retryAfterSeconds, RATE_LIMIT);
    }

    public RateLimitExceededException(String message, long retryAfterSeconds, String reason) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        this.reason = reason == null || reason.isBlank() ? RATE_LIMIT : reason;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getReason() {
        return reason;
    }
}
