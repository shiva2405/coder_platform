package com.coderplatform.exception;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        this("Login required");
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
