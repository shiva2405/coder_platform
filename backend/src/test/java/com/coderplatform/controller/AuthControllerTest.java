package com.coderplatform.controller;

import com.coderplatform.auth.CurrentUser;
import com.coderplatform.config.AuthConfig;
import com.coderplatform.exception.GlobalExceptionHandler;
import com.coderplatform.exception.UnauthorizedException;
import com.coderplatform.model.User;
import com.coderplatform.model.UserRole;
import com.coderplatform.service.AuthService;
import com.coderplatform.service.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private CurrentUser currentUser;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthConfig config = new AuthConfig();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, config, currentUser, new TokenHasher()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void meReturnsNullUserWhenAnonymous() throws Exception {
        when(currentUser.optional()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(nullValue()));
    }

    @Test
    void meReturnsSignedInUser() throws Exception {
        when(currentUser.optional()).thenReturn(Optional.of(user()));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("dev@localhost"))
                .andExpect(jsonPath("$.user.name").value("Dev"));
    }

    @Test
    void loginSetsSessionCookie() throws Exception {
        when(authService.login(any())).thenReturn(new AuthService.IssuedSession(user(), "raw-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@localhost\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("cp_session", "raw-token"))
                .andExpect(cookie().httpOnly("cp_session", true))
                .andExpect(jsonPath("$.user.email").value("dev@localhost"));
    }

    @Test
    void loginRejectsBadCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@localhost\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void githubRedirectsToProvider() throws Exception {
        when(authService.sanitizeNext("/dashboard")).thenReturn("/dashboard");
        when(authService.githubAuthorizationUrl(any())).thenReturn("https://github.com/login/oauth/authorize?state=x");

        mockMvc.perform(get("/api/auth/github").param("next", "/dashboard"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://github.com/login/oauth/authorize?state=x"))
                .andExpect(cookie().exists("cp_oauth_state"));
    }

    @Test
    void logoutClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("cp_session", 0));
    }

    private static User user() {
        User user = new User();
        user.setId(11L);
        user.setEmail("dev@localhost");
        user.setName("Dev");
        user.setRole(UserRole.USER);
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return user;
    }
}
