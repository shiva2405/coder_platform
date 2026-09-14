package com.coderplatform.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private String error;
    private int status;
    private Long retryAfterSeconds;
    private String reason;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String error, int status) {
        this.error = error;
        this.status = status;
    }

    public ApiErrorResponse(String error, int status, Long retryAfterSeconds, String reason) {
        this.error = error;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
        this.reason = reason;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(Long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
