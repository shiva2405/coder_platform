package com.coderplatform.exception;

public class SnippetNotFoundException extends RuntimeException {

    public SnippetNotFoundException(String slug) {
        super("Snippet not found: " + slug);
    }
}
