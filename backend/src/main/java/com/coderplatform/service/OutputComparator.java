package com.coderplatform.service;

import java.util.ArrayList;
import java.util.List;

public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    public static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);
        List<String> trimmed = new ArrayList<>(lines.length);
        for (String line : lines) {
            trimmed.add(rstrip(line));
        }
        int end = trimmed.size();
        while (end > 0 && trimmed.get(end - 1).isEmpty()) {
            end--;
        }
        if (end == 0) {
            return "";
        }
        return String.join("\n", trimmed.subList(0, end));
    }

    private static String rstrip(String line) {
        int index = line.length();
        while (index > 0 && Character.isWhitespace(line.charAt(index - 1))) {
            index--;
        }
        return line.substring(0, index);
    }
}
