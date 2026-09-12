package com.coderplatform.model;

public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILE_ERROR,
    ERROR;

    public static Verdict fromExecution(CodeExecutionResponse.Status status) {
        if (status == null) {
            return ERROR;
        }
        return switch (status) {
            case SUCCESS -> ACCEPTED;
            case COMPILE_ERROR -> COMPILE_ERROR;
            case RUNTIME_ERROR -> RUNTIME_ERROR;
            case TIMEOUT -> TIME_LIMIT_EXCEEDED;
            case MEMORY_EXCEEDED -> MEMORY_LIMIT_EXCEEDED;
            case STOPPED -> ERROR;
            case ERROR -> ERROR;
        };
    }
}
