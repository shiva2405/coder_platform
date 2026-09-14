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
    private SnippetVisibility visibility;
    private UserSummaryResponse owner;
    private boolean ownedByMe;

    public SnippetResponse() {
    }

    public static SnippetResponse from(Snippet snippet) {
        return from(snippet, null);
    }

    public static SnippetResponse from(Snippet snippet, User viewer) {
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
        response.visibility = snippet.getVisibility() != null ? snippet.getVisibility() : SnippetVisibility.PUBLIC;
        response.owner = UserSummaryResponse.from(snippet.getOwner());
        response.ownedByMe = snippet.getOwner() != null
                && viewer != null
                && snippet.getOwner().getId() != null
                && snippet.getOwner().getId().equals(viewer.getId());
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

    public SnippetVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(SnippetVisibility visibility) {
        this.visibility = visibility;
    }

    public UserSummaryResponse getOwner() {
        return owner;
    }

    public void setOwner(UserSummaryResponse owner) {
        this.owner = owner;
    }

    public boolean isOwnedByMe() {
        return ownedByMe;
    }

    public void setOwnedByMe(boolean ownedByMe) {
        this.ownedByMe = ownedByMe;
    }
}
