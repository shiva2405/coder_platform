package com.coderplatform.service;

import com.coderplatform.config.RateLimitConfig;
import com.coderplatform.config.SnippetConfig;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.model.User;
import com.coderplatform.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionQuotaServiceTest {

    private ExecutionQuotaService quota;

    @BeforeEach
    void setUp() {
        SnippetConfig snippetConfig = new SnippetConfig();
        RateLimitConfig rateLimitConfig = new RateLimitConfig();
        rateLimitConfig.setAnonymousPerHour(2);
        rateLimitConfig.setAuthenticatedPerHour(4);
        quota = new ExecutionQuotaService(new SnippetRateLimiter(snippetConfig), rateLimitConfig);
    }

    @Test
    void anonymousUsersAreLimitedMoreStrictlyThanSignedInUsers() {
        quota.consume(null, "1.1.1.1");
        quota.consume(null, "1.1.1.1");
        assertThatThrownBy(() -> quota.consume(null, "1.1.1.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("anonymous");

        User user = user(9L, UserRole.USER);
        quota.consume(user, "1.1.1.1");
        quota.consume(user, "1.1.1.1");
        quota.consume(user, "1.1.1.1");
        quota.consume(user, "1.1.1.1");
        assertThatThrownBy(() -> quota.consume(user, "1.1.1.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("4 per hour");
    }

    @Test
    void adminsAreNotRateLimited() {
        User admin = user(1L, UserRole.ADMIN);
        assertThatCode(() -> {
            for (int i = 0; i < 20; i++) {
                quota.consume(admin, "8.8.8.8");
            }
        }).doesNotThrowAnyException();
    }

    private static User user(long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail(role.name().toLowerCase() + id + "@localhost");
        user.setName("User");
        user.setRole(role);
        return user;
    }
}
