package com.coderplatform.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UpdateProblemRequest {

    @Size(max = 64, message = "Slug must be at most 64 characters")
    private String slug;

    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Size(max = 20000, message = "Description must be at most 20000 characters")
    private String description;

    private String difficulty;
    private List<String> tags;

    @Min(value = 100, message = "Time limit must be at least 100ms")
    @Max(value = 30000, message = "Time limit must be at most 30000ms")
    private Integer timeLimitMs;

    @Min(value = 1_048_576, message = "Memory limit must be at least 1MB")
    @Max(value = 536_870_912, message = "Memory limit must be at most 512MB")
    private Long memoryLimitBytes;

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Integer getTimeLimitMs() {
        return timeLimitMs;
    }

    public void setTimeLimitMs(Integer timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }

    public Long getMemoryLimitBytes() {
        return memoryLimitBytes;
    }

    public void setMemoryLimitBytes(Long memoryLimitBytes) {
        this.memoryLimitBytes = memoryLimitBytes;
    }
}
