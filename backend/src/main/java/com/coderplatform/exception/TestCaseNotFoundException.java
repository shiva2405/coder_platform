package com.coderplatform.exception;

public class TestCaseNotFoundException extends RuntimeException {

    public TestCaseNotFoundException(Long id) {
        super("Test case not found: " + id);
    }
}
