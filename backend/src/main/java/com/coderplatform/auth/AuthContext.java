package com.coderplatform.auth;

import com.coderplatform.model.User;

public final class AuthContext {

    public static final String USER_ATTR = "auth.user";
    public static final String TOKEN_ATTR = "auth.token";
    public static final String CLIENT_IP_ATTR = "auth.clientIp";

    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(User user, String token) {
        CURRENT_USER.set(user);
        CURRENT_TOKEN.set(token);
    }

    public static User getUser() {
        return CURRENT_USER.get();
    }

    public static String getToken() {
        return CURRENT_TOKEN.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_TOKEN.remove();
    }
}
