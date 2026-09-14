package com.coderplatform.model;

public class AuthProvidersResponse {

    private boolean local;
    private boolean github;

    public AuthProvidersResponse() {
    }

    public AuthProvidersResponse(boolean local, boolean github) {
        this.local = local;
        this.github = github;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isGithub() {
        return github;
    }

    public void setGithub(boolean github) {
        this.github = github;
    }
}
