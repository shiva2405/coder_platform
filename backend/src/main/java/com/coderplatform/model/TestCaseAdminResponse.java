package com.coderplatform.model;

public class TestCaseAdminResponse {

    private Long id;
    private String input;
    private String expectedOutput;
    private int points;
    private boolean sample;
    private int sortOrder;

    public static TestCaseAdminResponse from(TestCase testCase) {
        TestCaseAdminResponse response = new TestCaseAdminResponse();
        response.id = testCase.getId();
        response.input = testCase.getInput();
        response.expectedOutput = testCase.getExpectedOutput();
        response.points = testCase.getPoints();
        response.sample = testCase.isSample();
        response.sortOrder = testCase.getSortOrder();
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public boolean isSample() {
        return sample;
    }

    public void setSample(boolean sample) {
        this.sample = sample;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
