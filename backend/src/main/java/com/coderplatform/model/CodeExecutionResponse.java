package com.coderplatform.model;

public class CodeExecutionResponse {

    public enum Status {
        SUCCESS,
        COMPILE_ERROR,
        RUNTIME_ERROR,
        TIMEOUT,
        MEMORY_EXCEEDED,
        ERROR
    }

    private String output;
    private String error;
    private long executionTime; // in milliseconds
    private Status status;

    public CodeExecutionResponse() {
    }

    public CodeExecutionResponse(String output, String error, long executionTime, Status status) {
        this.output = output;
        this.error = error;
        this.executionTime = executionTime;
        this.status = status;
    }

    public static CodeExecutionResponse success(String output, long executionTime) {
        return new CodeExecutionResponse(output, "", executionTime, Status.SUCCESS);
    }

    public static CodeExecutionResponse compileError(String error, long executionTime) {
        return new CodeExecutionResponse("", error, executionTime, Status.COMPILE_ERROR);
    }

    public static CodeExecutionResponse runtimeError(String output, String error, long executionTime) {
        return new CodeExecutionResponse(output, error, executionTime, Status.RUNTIME_ERROR);
    }

    public static CodeExecutionResponse timeout(String output, long executionTime) {
        return new CodeExecutionResponse(output, "Execution timed out. Your program exceeded the time limit.", 
                                         executionTime, Status.TIMEOUT);
    }

    public static CodeExecutionResponse memoryExceeded(String output, long executionTime) {
        return new CodeExecutionResponse(output, "Memory limit exceeded. Your program used too much memory.", 
                                         executionTime, Status.MEMORY_EXCEEDED);
    }

    public static CodeExecutionResponse error(String error) {
        return new CodeExecutionResponse("", error, 0, Status.ERROR);
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
