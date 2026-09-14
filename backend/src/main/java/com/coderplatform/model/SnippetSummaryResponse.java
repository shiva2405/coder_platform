package com.coderplatform.model;

import java.time.Instant;

public class SnippetSummaryResponse {

    private String slug;
    private String language;
    private String title;
    private SnippetVisibility visibility;
    private Instant createdAt;
    private Instant updatedAt;
    private long viewCount;
    private String forkedFrom;

    public static SnippetSummaryResponse from(Snippet snippet) {
        SnippetSummaryResponse response = new SnippetSummaryResponse();
        response.slug = snippet.getSlug();
        response.language = snippet.getLanguage();
        response.title = snippet.getTitle();
        response.visibility = snippet.getVisibility();
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
