package com.coderplatform.model;

public class AuthResponse {

    private UserResponse user;

    public AuthResponse() {
    }

    public AuthResponse(UserResponse user) {
        this.user = user;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }
}
