package com.coderplatform.service;

import com.coderplatform.model.CodeExecutionResponse;

public interface LiveExecutionListener {

    void onStarted(String executionId);

    void onStdout(String chunk);

    void onStderr(String chunk);

    void onCompleted(CodeExecutionResponse result);
}
