package com.coderplatform.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveServerMessage {

    private String type;
    private String executionId;
    private String data;
    private String status;
    private Long executionTime;
    private String output;
    private String error;
    private String message;
    private Integer position;
    private Long estimatedWaitMs;
    private Long retryAfterSeconds;
    private String reason;

    public static LiveServerMessage queued(String executionId, int position, long estimatedWaitMs) {
        LiveServerMessage message = new LiveServerMessage();
        message.type = "queued";
        message.executionId = executionId;
        message.position = position;
        message.estimatedWaitMs = estimatedWaitMs;
        return message;
    }

    public static LiveServerMessage rejected(String text, long retryAfterSeconds, String reason) {
        LiveServerMessage message = new LiveServerMessage();
        message.type = "rejected";
        message.message = text;
        message.retryAfterSeconds = retryAfterSeconds;
        message.reason = reason;
        return message;
    }

    public static LiveServerMessage started(String executionId) {
        LiveServerMessage message = new LiveServerMessage();
        message.type = "started";
        message.executionId = executionId;
        return message;
    }

    public static LiveServerMessage stdout(String data) {
        LiveServerMessage message = new LiveServerMessage();
        message.type = "stdout";
        message.data = data;
        return message;
    }

    public static LiveServerMessage stderr(String data) {
        LiveServerMessage message = new LiveServerMessage();
        message.type = "stderr";
        message.data = data;
        return message;
    }

    public static LiveServerMessage done(CodeExecutionResponse result) {
        LiveServerMessage message = new LiveServerMessage();
        message.type = "done";
        message.status = result.getStatus() == null ? null : result.getStatus().name();
        message.executionTime = result.getExecutionTime();
        message.output = result.getOutput();
        message.error = result.getError();
        return message;
    }

    public static LiveServerMessage error(String text) {
        LiveServerMessage message = new LiveServerMessage();
        message.type = "error";
        message.message = text;
        return message;
    }

    public String getType() {
        return type;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getData() {
        return data;
    }

    public String getStatus() {
        return status;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public Integer getPosition() {
        return position;
    }

    public Long getEstimatedWaitMs() {
        return estimatedWaitMs;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getReason() {
        return reason;
    }
}
