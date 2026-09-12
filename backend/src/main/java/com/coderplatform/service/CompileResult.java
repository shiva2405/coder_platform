package com.coderplatform.service;

import com.coderplatform.model.CodeExecutionResponse;

public final class CompileResult {

    private final boolean success;
    private final CodeExecutionResponse errorResponse;

    private CompileResult(boolean success, CodeExecutionResponse errorResponse) {
        this.success = success;
        this.errorResponse = errorResponse;
    }

    public static CompileResult success() {
        return new CompileResult(true, null);
    }

    public static CompileResult failure(CodeExecutionResponse errorResponse) {
        return new CompileResult(false, errorResponse);
    }

    public boolean isSuccess() {
        return success;
    }

    public CodeExecutionResponse getErrorResponse() {
        return errorResponse;
    }
}
