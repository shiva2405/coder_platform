package com.coderplatform.auth;

import com.coderplatform.config.AuthConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class SessionCookies {

    public static final String OAUTH_STATE_COOKIE = "cp_oauth_state";
    public static final String OAUTH_NEXT_COOKIE = "cp_oauth_next";

    private SessionCookies() {
    }

    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    public static void writeSession(HttpServletResponse response, AuthConfig config, String rawToken) {
        Cookie cookie = base(config.getCookieName(), rawToken, config.isCookieSecure());
        cookie.setMaxAge((int) config.getSessionTtl().toSeconds());
        response.addCookie(cookie);
    }

    public static void clearSession(HttpServletResponse response, AuthConfig config) {
        Cookie cookie = base(config.getCookieName(), "", config.isCookieSecure());
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public static void writeOauth(HttpServletResponse response, String name, String value, boolean secure) {
        Cookie cookie = base(name, value, secure);
        cookie.setMaxAge(600);
        response.addCookie(cookie);
    }

    public static void clearOauth(HttpServletResponse response, boolean secure) {
        Cookie state = base(OAUTH_STATE_COOKIE, "", secure);
        state.setMaxAge(0);
        response.addCookie(state);
        Cookie next = base(OAUTH_NEXT_COOKIE, "", secure);
        next.setMaxAge(0);
        response.addCookie(next);
    }

    private static Cookie base(String name, String value, boolean secure) {
        Cookie cookie = new Cookie(name, value == null ? "" : value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setSecure(secure);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
