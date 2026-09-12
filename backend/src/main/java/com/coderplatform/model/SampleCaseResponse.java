package com.coderplatform.model;

public class SampleCaseResponse {

    private int index;
    private String input;
    private String expectedOutput;
    private int points;

    public static SampleCaseResponse from(TestCase testCase, int index) {
        SampleCaseResponse response = new SampleCaseResponse();
        response.index = index;
        response.input = testCase.getInput();
        response.expectedOutput = testCase.getExpectedOutput();
        response.points = testCase.getPoints();
        return response;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
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
}
