package com.coderplatform.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JudgeResultResponse {

    private Long id;
    private String problemSlug;
    private String language;
    private String code;
    private Verdict verdict;
    private long runtimeMs;
    private int passedCount;
    private int totalCount;
    private int score;
    private int maxScore;
    private String compileError;
    private Instant createdAt;
    private List<JudgeCaseResponse> cases = new ArrayList<>();

    public static JudgeResultResponse fromSubmission(Submission submission) {
        JudgeResultResponse response = new JudgeResultResponse();
        response.id = submission.getId();
        response.problemSlug = submission.getProblem() != null ? submission.getProblem().getSlug() : null;
        response.language = submission.getLanguage();
        response.code = submission.getCode();
        response.verdict = submission.getVerdict();
        response.runtimeMs = submission.getRuntimeMs();
        response.passedCount = submission.getPassedCount();
        response.totalCount = submission.getTotalCount();
        response.score = submission.getScore();
        response.maxScore = submission.getMaxScore();
        response.compileError = submission.getCompileError();
        response.createdAt = submission.getCreatedAt();
        List<JudgeCaseResponse> cases = new ArrayList<>();
        if (submission.getCaseResults() != null) {
            for (SubmissionCaseResult result : submission.getCaseResults()) {
                cases.add(JudgeCaseResponse.fromStored(result));
            }
        }
        response.cases = cases;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getCompileError() {
        return compileError;
    }

    public void setCompileError(String compileError) {
        this.compileError = compileError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<JudgeCaseResponse> getCases() {
        return cases;
    }

    public void setCases(List<JudgeCaseResponse> cases) {
        this.cases = cases;
    }
}
