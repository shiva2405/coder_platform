package com.coderplatform.model;

public enum Difficulty {
    EASY,
    MEDIUM,
    HARD;

    public static Difficulty from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Difficulty is required");
        }
        try {
            return Difficulty.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown difficulty: " + value);
        }
    }
}
