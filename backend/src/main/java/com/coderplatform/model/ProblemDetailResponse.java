package com.coderplatform.model;

import java.util.List;

public class ProblemDetailResponse {

    private String slug;
    private String title;
    private String description;
    private Difficulty difficulty;
    private List<String> tags;
    private int timeLimitMs;
    private long memoryLimitBytes;
    private List<SampleCaseResponse> samples;
    private int hiddenTestCount;

    public static ProblemDetailResponse from(Problem problem, List<TestCase> testCases) {
        ProblemDetailResponse response = new ProblemDetailResponse();
        response.slug = problem.getSlug();
        response.title = problem.getTitle();
        response.description = problem.getDescription();
        response.difficulty = problem.getDifficulty();
        response.tags = TagParser.parse(problem.getTags());
        response.timeLimitMs = problem.getTimeLimitMs();
        response.memoryLimitBytes = problem.getMemoryLimitBytes();

        int hidden = 0;
        int sampleIndex = 1;
        java.util.ArrayList<SampleCaseResponse> samples = new java.util.ArrayList<>();
        for (TestCase testCase : testCases) {
            if (testCase.isSample()) {
                samples.add(SampleCaseResponse.from(testCase, sampleIndex++));
            } else {
                hidden++;
            }
        }
        response.samples = samples;
        response.hiddenTestCount = hidden;
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

    public List<SampleCaseResponse> getSamples() {
        return samples;
    }

    public void setSamples(List<SampleCaseResponse> samples) {
        this.samples = samples;
    }

    public int getHiddenTestCount() {
        return hiddenTestCount;
    }

    public void setHiddenTestCount(int hiddenTestCount) {
        this.hiddenTestCount = hiddenTestCount;
    }
}
