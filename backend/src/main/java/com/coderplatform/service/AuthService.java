package com.coderplatform.service;

import com.coderplatform.config.AuthConfig;
import com.coderplatform.exception.ConflictException;
import com.coderplatform.exception.InvalidAuthException;
import com.coderplatform.exception.UnauthorizedException;
import com.coderplatform.model.AuthSession;
import com.coderplatform.model.LoginRequest;
import com.coderplatform.model.RegisterRequest;
import com.coderplatform.model.User;
import com.coderplatform.model.UserRole;
import com.coderplatform.repository.AuthSessionRepository;
import com.coderplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordHasher passwordHasher;
    private final TokenHasher tokenHasher;
    private final GitHubOAuthClient gitHubOAuthClient;
    private final AuthConfig authConfig;

    public AuthService(
            UserRepository userRepository,
            AuthSessionRepository sessionRepository,
            PasswordHasher passwordHasher,
            TokenHasher tokenHasher,
            GitHubOAuthClient gitHubOAuthClient,
            AuthConfig authConfig
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordHasher = passwordHasher;
        this.tokenHasher = tokenHasher;
        this.gitHubOAuthClient = gitHubOAuthClient;
        this.authConfig = authConfig;
    }

    public boolean isLocalEnabled() {
        return authConfig.isLocalEnabled();
    }

    public boolean isGithubEnabled() {
        return authConfig.isGithubEnabled();
    }

    public String githubAuthorizationUrl(String state) {
        if (!authConfig.isGithubEnabled()) {
            throw new InvalidAuthException("GitHub login is not configured");
        }
        return gitHubOAuthClient.authorizationUrl(state);
    }

    @Transactional
    public IssuedSession register(RegisterRequest request) {
        if (!authConfig.isLocalEnabled()) {
            throw new InvalidAuthException("Email and password login is disabled");
        }
        String email = normalizeEmail(request.getEmail());
        String name = normalizeName(request.getName());
        String password = request.getPassword();
        validateLocalCredentials(email, password, name);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPasswordHash(passwordHasher.hash(password));
        user.setRole(authConfig.isAdminEmail(email) ? UserRole.ADMIN : UserRole.USER);
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);
        logger.info("Registered local user {}", user.getId());
        return issueSession(user);
    }

    @Transactional
    public IssuedSession login(LoginRequest request) {
        if (!authConfig.isLocalEnabled()) {
            throw new InvalidAuthException("Email and password login is disabled");
        }
        String email = normalizeEmail(request.getEmail());
        String password = request.getPassword();
        if (email == null || password == null || password.isBlank()) {
            throw new UnauthorizedException("Invalid email or password");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        user.setLastLoginAt(Instant.now());
        applyAdminEmail(user);
        userRepository.save(user);
        return issueSession(user);
    }

    @Transactional
    public IssuedSession loginWithGithub(String code) {
        if (!authConfig.isGithubEnabled()) {
            throw new InvalidAuthException("GitHub login is not configured");
        }
        if (code == null || code.isBlank()) {
            throw new InvalidAuthException("GitHub login failed");
        }
        GitHubOAuthClient.GitHubProfile profile = gitHubOAuthClient.exchange(code);
        User user = userRepository.findByGithubId(profile.githubId())
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(profile.email()).orElse(null));
        if (user == null) {
            user = new User();
            user.setEmail(profile.email());
            user.setName(profile.name());
            user.setAvatarUrl(profile.avatarUrl());
            user.setGithubId(profile.githubId());
            user.setRole(authConfig.isAdminEmail(profile.email()) ? UserRole.ADMIN : UserRole.USER);
        } else {
            if (user.getGithubId() == null) {
                user.setGithubId(profile.githubId());
            }
            if (profile.avatarUrl() != null) {
                user.setAvatarUrl(profile.avatarUrl());
            }
            if (user.getName() == null || user.getName().isBlank()) {
                user.setName(profile.name());
            }
            applyAdminEmail(user);
        }
        user.setLastLoginAt(Instant.now());
        user = userRepository.save(user);
        logger.info("Authenticated GitHub user {}", user.getId());
        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthSession> session = sessionRepository.findByTokenHash(tokenHasher.hash(rawToken));
        if (session.isEmpty() || session.get().isExpired() || session.get().getUser() == null) {
            return Optional.empty();
        }
        return Optional.of(session.get().getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionRepository.deleteByTokenHash(tokenHasher.hash(rawToken));
    }

    @Transactional
    public void logoutAll(User user) {
        if (user != null) {
            sessionRepository.deleteByUser(user);
        }
    }

    public String sanitizeNext(String next) {
        if (next == null || next.isBlank()) {
            return "/";
        }
        String trimmed = next.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.contains("://")) {
            return "/";
        }
        return trimmed;
    }

    public String frontendRedirect(String next) {
        String path = sanitizeNext(next);
        String base = authConfig.getFrontendBaseUrl();
        if (base == null || base.isBlank()) {
            return path;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private IssuedSession issueSession(User user) {
        String rawToken = tokenHasher.newToken();
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTokenHash(tokenHasher.hash(rawToken));
        session.setExpiresAt(Instant.now().plus(authConfig.getSessionTtl()));
        sessionRepository.save(session);
        return new IssuedSession(user, rawToken);
    }

    private void applyAdminEmail(User user) {
        if (authConfig.isAdminEmail(user.getEmail()) && user.getRole() != UserRole.ADMIN) {
            user.setRole(UserRole.ADMIN);
        }
    }

    private void validateLocalCredentials(String email, String password, String name) {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new InvalidAuthException("Email is invalid");
        }
        if (name == null) {
            throw new InvalidAuthException("Name is required");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new InvalidAuthException("Password must be between 8 and 128 characters");
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record IssuedSession(User user, String rawToken) {
    }
}
