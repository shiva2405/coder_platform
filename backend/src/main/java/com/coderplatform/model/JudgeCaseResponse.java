package com.coderplatform.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JudgeCaseResponse {

    private int index;
    private boolean sample;
    private Verdict verdict;
    private long runtimeMs;
    private int points;
    private String input;
    private String expectedOutput;
    private String actualOutput;
    private String error;

    public static JudgeCaseResponse publicView(int index, boolean sample, Verdict verdict, long runtimeMs, int points,
                                               String input, String expectedOutput, String actualOutput, String error) {
        JudgeCaseResponse response = new JudgeCaseResponse();
        response.index = index;
        response.sample = sample;
        response.verdict = verdict;
        response.runtimeMs = runtimeMs;
        response.points = points;
        if (sample) {
            response.input = input;
            response.expectedOutput = expectedOutput;
            response.actualOutput = actualOutput;
            response.error = error;
        }
        return response;
    }

    public static JudgeCaseResponse fromStored(SubmissionCaseResult result) {
        return publicView(
                result.getSortOrder(),
                result.isSample(),
                result.getVerdict(),
                result.getRuntimeMs(),
                result.getPoints(),
                result.getInput(),
                result.getExpectedOutput(),
                result.getActualOutput(),
                result.getError()
        );
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public boolean isSample() {
        return sample;
    }

    public void setSample(boolean sample) {
        this.sample = sample;
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

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public void setActualOutput(String actualOutput) {
        this.actualOutput = actualOutput;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
