package com.coderplatform.exception;

public class UnauthorizedAdminException extends RuntimeException {

    public UnauthorizedAdminException() {
        super("Unauthorized");
    }
}
