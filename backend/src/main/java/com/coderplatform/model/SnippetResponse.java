package com.coderplatform.model;

import java.time.Instant;

public class SnippetResponse {

    private String slug;
    private String language;
    private String code;
    private String stdin;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private long viewCount;
    private String forkedFrom;

    public SnippetResponse() {
    }

    public static SnippetResponse from(Snippet snippet) {
        SnippetResponse response = new SnippetResponse();
        response.slug = snippet.getSlug();
        response.language = snippet.getLanguage();
        response.code = snippet.getCode();
        response.stdin = snippet.getStdin() != null ? snippet.getStdin() : "";
        response.title = snippet.getTitle();
        response.createdAt = snippet.getCreatedAt();
        response.updatedAt = snippet.getUpdatedAt();
        response.viewCount = snippet.getViewCount();
        response.forkedFrom = snippet.getForkedFromSlug();
        return response;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public String getForkedFrom() {
        return forkedFrom;
    }

    public void setForkedFrom(String forkedFrom) {
        this.forkedFrom = forkedFrom;
    }
}
