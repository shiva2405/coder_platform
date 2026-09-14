package com.coderplatform.exception;

public class AdmissionRejectedException extends RateLimitExceededException {

    public static final String QUEUE_FULL = "QUEUE_FULL";
    public static final String CLIENT_BUSY = "CLIENT_BUSY";
    public static final String QUEUE_TIMEOUT = "QUEUE_TIMEOUT";

    public AdmissionRejectedException(String message, long retryAfterSeconds, String reason) {
        super(message, retryAfterSeconds, reason);
    }

    public static AdmissionRejectedException queueFull(long retryAfterSeconds) {
        return new AdmissionRejectedException(
                "Execution queue is full. Try again shortly.",
                retryAfterSeconds,
                QUEUE_FULL
        );
    }

    public static AdmissionRejectedException clientBusy(long retryAfterSeconds) {
        return new AdmissionRejectedException(
                "You already have too many programs waiting or running. Try again shortly.",
                retryAfterSeconds,
                CLIENT_BUSY
        );
    }

    public static AdmissionRejectedException queueTimeout(long retryAfterSeconds) {
        return new AdmissionRejectedException(
                "Waited too long in the execution queue. Try again shortly.",
                retryAfterSeconds,
                QUEUE_TIMEOUT
        );
    }
}
