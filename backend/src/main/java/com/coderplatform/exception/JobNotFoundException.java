package com.coderplatform.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String id) {
        super("Execution job not found: " + id);
    }
}
