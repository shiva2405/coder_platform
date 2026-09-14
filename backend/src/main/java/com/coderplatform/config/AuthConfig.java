package com.coderplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthConfig {

    private boolean localEnabled = true;
    private String cookieName = "cp_session";
    private boolean cookieSecure = false;
    private int sessionTtlDays = 7;
    private String frontendBaseUrl = "http://localhost:3000";
    private List<String> adminEmails = new ArrayList<>(List.of("admin@localhost"));
    private GitHub github = new GitHub();

    public boolean isLocalEnabled() {
        return localEnabled;
    }

    public void setLocalEnabled(boolean localEnabled) {
        this.localEnabled = localEnabled;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public int getSessionTtlDays() {
        return sessionTtlDays;
    }

    public void setSessionTtlDays(int sessionTtlDays) {
        this.sessionTtlDays = sessionTtlDays;
    }

    public Duration getSessionTtl() {
        return Duration.ofDays(Math.max(1, sessionTtlDays));
    }

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public List<String> getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(List<String> adminEmails) {
        this.adminEmails = expandEmails(adminEmails);
    }

    public GitHub getGithub() {
        return github;
    }

    public void setGithub(GitHub github) {
        this.github = github != null ? github : new GitHub();
    }

    public boolean isGithubEnabled() {
        return github != null
                && !isBlank(github.getClientId())
                && !isBlank(github.getClientSecret());
    }

    public boolean isAdminEmail(String email) {
        if (isBlank(email) || adminEmails == null) {
            return false;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return adminEmails.stream()
                .filter(value -> !isBlank(value))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    private static List<String> expandEmails(List<String> values) {
        List<String> emails = new ArrayList<>();
        if (values == null) {
            return emails;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    emails.add(part.trim());
                }
            }
        }
        return emails;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static class GitHub {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:3000/api/auth/github/callback";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }
}
