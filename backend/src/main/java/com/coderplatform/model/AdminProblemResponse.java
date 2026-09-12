package com.coderplatform.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AdminProblemResponse {

    private Long id;
    private String slug;
    private String title;
    private String description;
    private Difficulty difficulty;
    private List<String> tags;
    private int timeLimitMs;
    private long memoryLimitBytes;
    private Instant createdAt;
    private Instant updatedAt;
    private List<TestCaseAdminResponse> testCases = new ArrayList<>();

    public static AdminProblemResponse from(Problem problem, List<TestCase> testCases) {
        AdminProblemResponse response = new AdminProblemResponse();
        response.id = problem.getId();
        response.slug = problem.getSlug();
        response.title = problem.getTitle();
        response.description = problem.getDescription();
        response.difficulty = problem.getDifficulty();
        response.tags = TagParser.parse(problem.getTags());
        response.timeLimitMs = problem.getTimeLimitMs();
        response.memoryLimitBytes = problem.getMemoryLimitBytes();
        response.createdAt = problem.getCreatedAt();
        response.updatedAt = problem.getUpdatedAt();
        List<TestCaseAdminResponse> cases = new ArrayList<>();
        for (TestCase testCase : testCases) {
            cases.add(TestCaseAdminResponse.from(testCase));
        }
        response.testCases = cases;
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
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

    public List<TestCaseAdminResponse> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<TestCaseAdminResponse> testCases) {
        this.testCases = testCases;
    }
}
