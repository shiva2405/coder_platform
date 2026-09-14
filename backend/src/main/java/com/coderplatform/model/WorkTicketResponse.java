package com.coderplatform.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkTicketResponse {

    private String id;
    private WorkState state;
    private Integer position;
    private Long estimatedWaitMs;
    private Long retryAfterSeconds;
    private String reason;
    private String error;
    private Object result;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public WorkState getState() {
        return state;
    }

    public void setState(WorkState state) {
        this.state = state;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Long getEstimatedWaitMs() {
        return estimatedWaitMs;
    }

    public void setEstimatedWaitMs(Long estimatedWaitMs) {
        this.estimatedWaitMs = estimatedWaitMs;
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

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
