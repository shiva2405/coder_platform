package com.coderplatform.exception;

public class ProblemNotFoundException extends RuntimeException {

    public ProblemNotFoundException(String slug) {
        super("Problem not found: " + slug);
    }
}
