package com.coderplatform.model;

import java.util.List;

public class ProblemSummaryResponse {

    private String slug;
    private String title;
    private Difficulty difficulty;
    private List<String> tags;
    private int timeLimitMs;
    private long memoryLimitBytes;
    private int sampleCount;
    private int totalTestCases;

    public static ProblemSummaryResponse from(Problem problem, int sampleCount, int totalTestCases) {
        ProblemSummaryResponse response = new ProblemSummaryResponse();
        response.slug = problem.getSlug();
        response.title = problem.getTitle();
        response.difficulty = problem.getDifficulty();
        response.tags = TagParser.parse(problem.getTags());
        response.timeLimitMs = problem.getTimeLimitMs();
        response.memoryLimitBytes = problem.getMemoryLimitBytes();
        response.sampleCount = sampleCount;
        response.totalTestCases = totalTestCases;
        return response;
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

    public int getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(int sampleCount) {
        this.sampleCount = sampleCount;
    }

    public int getTotalTestCases() {
        return totalTestCases;
    }

    public void setTotalTestCases(int totalTestCases) {
        this.totalTestCases = totalTestCases;
    }
}
