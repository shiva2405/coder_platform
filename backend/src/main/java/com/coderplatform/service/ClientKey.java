package com.coderplatform.service;

import com.coderplatform.model.User;

public final class ClientKey {

    private final String id;
    private final boolean admin;

    private ClientKey(String id, boolean admin) {
        this.id = id;
        this.admin = admin;
    }

    public static ClientKey of(User user, String ip) {
        if (user != null && user.getId() != null) {
            return new ClientKey("user:" + user.getId(), user.isAdmin());
        }
        String safe = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        return new ClientKey("ip:" + safe, false);
    }

    public static ClientKey anonymous(String ip) {
        return of(null, ip);
    }

    public String getId() {
        return id;
    }

    public boolean isAdmin() {
        return admin;
    }

    @Override
    public String toString() {
        return id;
    }
}
