package com.coderplatform.controller;

import com.coderplatform.auth.AuthContext;
import com.coderplatform.auth.CurrentUser;
import com.coderplatform.auth.SessionCookies;
import com.coderplatform.config.AuthConfig;
import com.coderplatform.model.AuthProvidersResponse;
import com.coderplatform.model.AuthResponse;
import com.coderplatform.model.LoginRequest;
import com.coderplatform.model.RegisterRequest;
import com.coderplatform.model.UserResponse;
import com.coderplatform.service.AuthService;
import com.coderplatform.service.TokenHasher;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthConfig authConfig;
    private final CurrentUser currentUser;
    private final TokenHasher tokenHasher;

    public AuthController(
            AuthService authService,
            AuthConfig authConfig,
            CurrentUser currentUser,
            TokenHasher tokenHasher
    ) {
        this.authService = authService;
        this.authConfig = authConfig;
        this.currentUser = currentUser;
        this.tokenHasher = tokenHasher;
    }

    @GetMapping("/providers")
    public AuthProvidersResponse providers() {
        return new AuthProvidersResponse(authService.isLocalEnabled(), authService.isGithubEnabled());
    }

    @GetMapping("/me")
    public AuthResponse me() {
        return new AuthResponse(currentUser.optional().map(UserResponse::from).orElse(null));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthService.IssuedSession session = authService.register(request);
        SessionCookies.writeSession(response, authConfig, session.rawToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(UserResponse.from(session.user())));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.IssuedSession session = authService.login(request);
        SessionCookies.writeSession(response, authConfig, session.rawToken());
        return new AuthResponse(UserResponse.from(session.user()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authService.logout(AuthContext.getToken());
        SessionCookies.clearSession(response, authConfig);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/github")
    public ResponseEntity<Void> github(
            @RequestParam(value = "next", required = false) String next,
            HttpServletResponse response
    ) {
        String state = tokenHasher.newToken();
        SessionCookies.writeOauth(response, SessionCookies.OAUTH_STATE_COOKIE, state, authConfig.isCookieSecure());
        SessionCookies.writeOauth(
                response,
                SessionCookies.OAUTH_NEXT_COOKIE,
                authService.sanitizeNext(next),
                authConfig.isCookieSecure()
        );
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authService.githubAuthorizationUrl(state))
                .build();
    }

    @GetMapping("/github/callback")
    public ResponseEntity<Void> githubCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response
    ) {
        String expectedState = SessionCookies.read(request, SessionCookies.OAUTH_STATE_COOKIE);
        String next = SessionCookies.read(request, SessionCookies.OAUTH_NEXT_COOKIE);
        SessionCookies.clearOauth(response, authConfig.isCookieSecure());
        if (error != null || code == null || expectedState == null || !expectedState.equals(state)) {
            return redirectTo(authService.frontendRedirect("/login?error=github"));
        }
        try {
            AuthService.IssuedSession session = authService.loginWithGithub(code);
            SessionCookies.writeSession(response, authConfig, session.rawToken());
            return redirectTo(authService.frontendRedirect(next));
        } catch (Exception e) {
            return redirectTo(authService.frontendRedirect("/login?error=github"));
        }
    }

    private static ResponseEntity<Void> redirectTo(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
