package com.coderplatform.exception;

public class SubmissionNotFoundException extends RuntimeException {

    public SubmissionNotFoundException(Long id) {
        super("Submission not found: " + id);
    }
}
