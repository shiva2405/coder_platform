package com.coderplatform.model;

import java.time.Instant;

public class SubmissionSummaryResponse {

    private Long id;
    private String problemSlug;
    private String language;
    private Verdict verdict;
    private long runtimeMs;
    private int passedCount;
    private int totalCount;
    private int score;
    private int maxScore;
    private Instant createdAt;

    public static SubmissionSummaryResponse from(Submission submission) {
        SubmissionSummaryResponse response = new SubmissionSummaryResponse();
        response.id = submission.getId();
        response.problemSlug = submission.getProblem() != null ? submission.getProblem().getSlug() : null;
        response.language = submission.getLanguage();
        response.verdict = submission.getVerdict();
        response.runtimeMs = submission.getRuntimeMs();
        response.passedCount = submission.getPassedCount();
        response.totalCount = submission.getTotalCount();
        response.score = submission.getScore();
        response.maxScore = submission.getMaxScore();
        response.createdAt = submission.getCreatedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProblemSlug() {
        return problemSlug;
    }

    public void setProblemSlug(String problemSlug) {
        this.problemSlug = problemSlug;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public void setVerdict(Verdict verdict) {
        this.verdict = verdict;
    }

    public long getRuntimeMs() {
        return runtimeMs;
    }

    public void setRuntimeMs(long runtimeMs) {
        this.runtimeMs = runtimeMs;
    }

    public int getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(int passedCount) {
        this.passedCount = passedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = maxScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
