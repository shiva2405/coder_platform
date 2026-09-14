package com.coderplatform.service;

import com.coderplatform.config.AuthConfig;
import com.coderplatform.exception.InvalidAuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class GitHubOAuthClient {

    private static final Logger logger = LoggerFactory.getLogger(GitHubOAuthClient.class);
    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    private final AuthConfig authConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubOAuthClient(AuthConfig authConfig, ObjectMapper objectMapper) {
        this.authConfig = authConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String authorizationUrl(String state) {
        AuthConfig.GitHub github = authConfig.getGithub();
        return AUTHORIZE_URL
                + "?client_id=" + encode(github.getClientId())
                + "&redirect_uri=" + encode(github.getRedirectUri())
                + "&scope=" + encode("read:user user:email")
                + "&state=" + encode(state);
    }

    public GitHubProfile exchange(String code) {
        String accessToken = requestAccessToken(code);
        JsonNode profile = getJson(USER_URL, accessToken);
        String githubId = text(profile, "id");
        if (githubId == null) {
            throw new InvalidAuthException("GitHub login failed");
        }
        String login = text(profile, "login");
        String name = firstNonBlank(text(profile, "name"), login, "GitHub User");
        String avatarUrl = text(profile, "avatar_url");
        String email = firstNonBlank(text(profile, "email"), primaryEmail(accessToken));
        if (email == null) {
            email = "gh-" + githubId + "@users.noreply.github.com";
        }
        return new GitHubProfile(githubId, email, name, avatarUrl);
    }

    private String requestAccessToken(String code) {
        AuthConfig.GitHub github = authConfig.getGithub();
        String body = "client_id=" + encode(github.getClientId())
                + "&client_secret=" + encode(github.getClientSecret())
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(github.getRedirectUri());
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode json = send(request);
        String token = text(json, "access_token");
        if (token == null) {
            logger.warn("GitHub token exchange failed: {}", json);
            throw new InvalidAuthException("GitHub login failed");
        }
        return token;
    }

    private String primaryEmail(String accessToken) {
        try {
            JsonNode emails = getJson(EMAILS_URL, accessToken);
            if (!emails.isArray()) {
                return null;
            }
            String fallback = null;
            for (JsonNode emailNode : emails) {
                String email = text(emailNode, "email");
                if (email == null) {
                    continue;
                }
                boolean verified = emailNode.path("verified").asBoolean(false);
                boolean primary = emailNode.path("primary").asBoolean(false);
                if (verified && primary) {
                    return email;
                }
                if (verified && fallback == null) {
                    fallback = email;
                }
            }
            return fallback;
        } catch (Exception e) {
            logger.debug("Could not load GitHub emails", e);
            return null;
        }
    }

    private JsonNode getJson(String url, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "coder-platform")
                .GET()
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                logger.warn("GitHub API {} returned {}", request.uri(), response.statusCode());
                throw new InvalidAuthException("GitHub login failed");
            }
            return objectMapper.readTree(response.body());
        } catch (InvalidAuthException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidAuthException("GitHub login failed");
        } catch (Exception e) {
            logger.warn("GitHub API request failed", e);
            throw new InvalidAuthException("GitHub login failed");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record GitHubProfile(String githubId, String email, String name, String avatarUrl) {
    }
}
