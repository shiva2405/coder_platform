package com.coderplatform.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP when the app sits behind nginx.
 * Prefers {@code X-Real-IP} (set by nginx to the connecting address), then the
 * right-most {@code X-Forwarded-For} hop (the address nginx appended), then
 * {@code remoteAddr}. Taking the left-most forwarded hop would trust a
 * client-supplied spoofed value.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (isUsable(realIp)) {
            return normalize(realIp);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = normalize(parts[i]);
                if (isUsable(candidate)) {
                    return candidate;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return isUsable(remote) ? normalize(remote) : "unknown";
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.regionMatches(true, 0, "for=", 0, 4)) {
            value = value.substring(4).trim();
        }
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.startsWith("[") && value.contains("]")) {
            int end = value.indexOf(']');
            value = value.substring(1, end);
        } else if (value.regionMatches(true, 0, "::ffff:", 0, 7)) {
            value = value.substring(7);
        } else if (isIpv4WithPort(value)) {
            value = value.substring(0, value.lastIndexOf(':'));
        }
        return value.trim();
    }

    private static String firstHeaderValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int comma = header.indexOf(',');
        return comma < 0 ? header.trim() : header.substring(0, comma).trim();
    }

    private static boolean isIpv4WithPort(String value) {
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || colon == value.length() - 1 || value.indexOf(':') != colon) {
            return false;
        }
        String host = value.substring(0, colon);
        String port = value.substring(colon + 1);
        return host.indexOf('.') > 0 && port.chars().allMatch(Character::isDigit);
    }

    private static boolean isUsable(String ip) {
        if (ip == null) {
            return false;
        }
        String value = ip.trim();
        return !value.isEmpty()
                && !"unknown".equalsIgnoreCase(value)
                && !"undefined".equalsIgnoreCase(value);
    }
}
