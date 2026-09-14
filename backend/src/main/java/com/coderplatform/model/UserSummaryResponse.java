package com.coderplatform.model;

public class UserSummaryResponse {

    private Long id;
    private String name;
    private String avatarUrl;

    public static UserSummaryResponse from(User user) {
        if (user == null) {
            return null;
        }
        UserSummaryResponse response = new UserSummaryResponse();
        response.id = user.getId();
        response.name = user.getName();
        response.avatarUrl = user.getAvatarUrl();
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
