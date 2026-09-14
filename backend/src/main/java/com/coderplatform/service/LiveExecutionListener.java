package com.coderplatform.service;

import com.coderplatform.model.CodeExecutionResponse;

public interface LiveExecutionListener {

    default void onQueued(String executionId, int position, long estimatedWaitMs) {
    }

    void onStarted(String executionId);

    void onStdout(String chunk);

    void onStderr(String chunk);

    void onCompleted(CodeExecutionResponse result);

    default void onRejected(String message, long retryAfterSeconds, String reason) {
    }
}
