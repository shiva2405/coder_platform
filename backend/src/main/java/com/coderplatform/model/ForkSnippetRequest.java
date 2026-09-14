package com.coderplatform.model;

import jakarta.validation.constraints.Size;

public class ForkSnippetRequest {

    private String language;
    private String code;
    private String stdin;

    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    private SnippetVisibility visibility;

    public ForkSnippetRequest() {
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public SnippetVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(SnippetVisibility visibility) {
        this.visibility = visibility;
    }
}
