package com.coderplatform.service;

import com.coderplatform.config.AuthConfig;
import com.coderplatform.exception.ConflictException;
import com.coderplatform.exception.UnauthorizedException;
import com.coderplatform.model.AuthSession;
import com.coderplatform.model.LoginRequest;
import com.coderplatform.model.RegisterRequest;
import com.coderplatform.model.User;
import com.coderplatform.model.UserRole;
import com.coderplatform.repository.AuthSessionRepository;
import com.coderplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    @Mock
    private GitHubOAuthClient gitHubOAuthClient;

    private AuthService authService;
    private PasswordHasher passwordHasher;
    private TokenHasher tokenHasher;

    @BeforeEach
    void setUp() {
        AuthConfig config = new AuthConfig();
        config.setAdminEmails(List.of("admin@localhost"));
        config.getGithub().setClientId("client-id");
        config.getGithub().setClientSecret("client-secret");
        passwordHasher = new PasswordHasher();
        tokenHasher = new TokenHasher();
        authService = new AuthService(
                userRepository,
                sessionRepository,
                passwordHasher,
                tokenHasher,
                gitHubOAuthClient,
                config
        );
    }

    @Test
    void registerCreatesUserAndSession() {
        when(userRepository.existsByEmailIgnoreCase("dev@localhost")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(5L);
            return user;
        });
        when(sessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest();
        request.setEmail("dev@localhost");
        request.setPassword("password1");
        request.setName("Dev");

        AuthService.IssuedSession session = authService.register(request);

        assertThat(session.user().getEmail()).isEqualTo("dev@localhost");
        assertThat(session.user().getRole()).isEqualTo(UserRole.USER);
        assertThat(session.rawToken()).isNotBlank();
        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(tokenHasher.hash(session.rawToken()));
    }

    @Test
    void registerPromotesConfiguredAdminEmail() {
        when(userRepository.existsByEmailIgnoreCase("admin@localhost")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(sessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest();
        request.setEmail("admin@localhost");
        request.setPassword("password1");
        request.setName("Admin");

        assertThat(authService.register(request).user().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("dev@localhost")).thenReturn(true);
        RegisterRequest request = new RegisterRequest();
        request.setEmail("dev@localhost");
        request.setPassword("password1");
        request.setName("Dev");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void loginAuthenticatesAndIssuesSession() {
        User user = new User();
        user.setId(3L);
        user.setEmail("dev@localhost");
        user.setName("Dev");
        user.setPasswordHash(passwordHasher.hash("password1"));
        user.setRole(UserRole.USER);
        when(userRepository.findByEmailIgnoreCase("dev@localhost")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginRequest request = new LoginRequest();
        request.setEmail("dev@localhost");
        request.setPassword("password1");

        AuthService.IssuedSession session = authService.login(request);
        assertThat(session.user().getId()).isEqualTo(3L);
        assertThat(session.user().getLastLoginAt()).isNotNull();
    }

    @Test
    void loginRejectsBadPassword() {
        User user = new User();
        user.setId(3L);
        user.setEmail("dev@localhost");
        user.setPasswordHash(passwordHasher.hash("password1"));
        when(userRepository.findByEmailIgnoreCase("dev@localhost")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setEmail("dev@localhost");
        request.setPassword("nope");

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void authenticateReturnsUserForValidSession() {
        User user = new User();
        user.setId(9L);
        String raw = "abc123";
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTokenHash(tokenHasher.hash(raw));
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(sessionRepository.findByTokenHash(tokenHasher.hash(raw))).thenReturn(Optional.of(session));

        assertThat(authService.authenticate(raw)).contains(user);
        assertThat(authService.authenticate("missing")).isEmpty();
    }

    @Test
    void logoutDeletesCurrentSession() {
        String raw = "tokentoken";
        authService.logout(raw);
        verify(sessionRepository).deleteByTokenHash(tokenHasher.hash(raw));
    }

    @Test
    void githubLoginCreatesOrLinksUser() {
        when(gitHubOAuthClient.exchange("code-1")).thenReturn(
                new GitHubOAuthClient.GitHubProfile("42", "octo@example.com", "Octo", "https://avatar")
        );
        when(userRepository.findByGithubId("42")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("octo@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(12L);
            return user;
        });
        when(sessionRepository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthService.IssuedSession session = authService.loginWithGithub("code-1");
        assertThat(session.user().getGithubId()).isEqualTo("42");
        assertThat(session.user().getEmail()).isEqualTo("octo@example.com");
        assertThat(session.user().getAvatarUrl()).isEqualTo("https://avatar");
    }

    @Test
    void sanitizeNextRejectsOpenRedirects() {
        assertThat(authService.sanitizeNext("/dashboard")).isEqualTo("/dashboard");
        assertThat(authService.sanitizeNext("https://evil.test")).isEqualTo("/");
        assertThat(authService.sanitizeNext("//evil.test")).isEqualTo("/");
    }
}
