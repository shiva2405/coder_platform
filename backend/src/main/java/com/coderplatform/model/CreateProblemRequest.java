package com.coderplatform.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class CreateProblemRequest {

    @NotBlank(message = "Slug is required")
    @Size(max = 64, message = "Slug must be at most 64 characters")
    private String slug;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 20000, message = "Description must be at most 20000 characters")
    private String description;

    @NotNull(message = "Difficulty is required")
    private String difficulty;

    private List<String> tags = new ArrayList<>();

    @Min(value = 100, message = "Time limit must be at least 100ms")
    @Max(value = 30000, message = "Time limit must be at most 30000ms")
    private int timeLimitMs = 1000;

    @Min(value = 1_048_576, message = "Memory limit must be at least 1MB")
    @Max(value = 536_870_912, message = "Memory limit must be at most 512MB")
    private long memoryLimitBytes = 67_108_864;

    @Valid
    private List<CreateTestCaseRequest> testCases = new ArrayList<>();

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

    public int getTimeLimitMs() {
        return timeLimitMs;
    }

    public void setTimeLimitMs(int timeLimitMs) {
        this.timeLimitMs = timeLimitMs;
    }

    public long getMemoryLimitBytes() {
        return memoryLimitBytes;
    }

    public void setMemoryLimitBytes(long memoryLimitBytes) {
        this.memoryLimitBytes = memoryLimitBytes;
    }

    public List<CreateTestCaseRequest> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<CreateTestCaseRequest> testCases) {
        this.testCases = testCases;
    }
}
