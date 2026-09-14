package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import com.coderplatform.exception.InvalidSnippetException;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.exception.SnippetNotFoundException;
import com.coderplatform.exception.ForbiddenException;
import com.coderplatform.exception.UnauthorizedException;
import com.coderplatform.model.CreateSnippetRequest;
import com.coderplatform.model.ForkSnippetRequest;
import com.coderplatform.model.Snippet;
import com.coderplatform.model.SnippetListResponse;
import com.coderplatform.model.SnippetResponse;
import com.coderplatform.model.SnippetVisibility;
import com.coderplatform.model.UpdateSnippetRequest;
import com.coderplatform.model.User;
import com.coderplatform.model.UserRole;
import com.coderplatform.repository.SnippetRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnippetServiceTest {

    @Mock
    private SnippetRepository snippetRepository;

    @Mock
    private SlugGenerator slugGenerator;

    @Mock
    private SnippetRateLimiter rateLimiter;

    private SnippetService snippetService;

    @BeforeEach
    void setUp() {
        SnippetConfig config = new SnippetConfig();
        snippetService = new SnippetService(snippetRepository, slugGenerator, rateLimiter, config);
    }

    @Test
    void createPersistsValidatedSnippet() {
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(slugGenerator.generate(8)).thenReturn("abc12345");
        when(snippetRepository.existsBySlug("abc12345")).thenReturn(false);
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(invocation -> {
            Snippet snippet = invocation.getArgument(0);
            snippet.setId(10L);
            snippet.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            snippet.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return snippet;
        });

        CreateSnippetRequest request = new CreateSnippetRequest("python", "print(1)", "hello", "Demo");
        SnippetResponse response = snippetService.create(request, "1.1.1.1", owner(7L));

        assertThat(response.getSlug()).isEqualTo("abc12345");
        assertThat(response.getLanguage()).isEqualTo("python");
        assertThat(response.getCode()).isEqualTo("print(1)");
        assertThat(response.getStdin()).isEqualTo("hello");
        assertThat(response.getTitle()).isEqualTo("Demo");
        assertThat(response.getViewCount()).isZero();
        assertThat(response.getForkedFrom()).isNull();
        assertThat(response.getVisibility()).isEqualTo(SnippetVisibility.PUBLIC);
        assertThat(response.isOwnedByMe()).isTrue();
    }

    @Test
    void createRequiresLogin() {
        CreateSnippetRequest request = new CreateSnippetRequest("python", "print(1)", "", null);

        assertThatThrownBy(() -> snippetService.create(request, "1.1.1.1", null))
                .isInstanceOf(UnauthorizedException.class);
        verify(snippetRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownLanguage() {
        CreateSnippetRequest request = new CreateSnippetRequest("cobol", "DISPLAY 'HI'.", "", null);

        assertThatThrownBy(() -> snippetService.create(request, "1.1.1.1", owner(1L)))
                .isInstanceOf(InvalidSnippetException.class)
                .hasMessageContaining("Unsupported language");
        verify(snippetRepository, never()).save(any());
        verify(rateLimiter, never()).tryAcquire(anyString());
    }

    @Test
    void createRejectsCodeLargerThan256Kb() {
        String huge = "a".repeat(262145);
        CreateSnippetRequest request = new CreateSnippetRequest("python", huge, "", null);

        assertThatThrownBy(() -> snippetService.create(request, "1.1.1.1", owner(1L)))
                .isInstanceOf(InvalidSnippetException.class)
                .hasMessageContaining("256KB");
        verify(snippetRepository, never()).save(any());
    }

    @Test
    void createEnforcesHourlyUserLimit() {
        when(rateLimiter.tryAcquire("snippet:user:9", 60)).thenReturn(false);
        when(rateLimiter.retryAfterSeconds("snippet:user:9")).thenReturn(1200L);

        CreateSnippetRequest request = new CreateSnippetRequest("python", "print(1)", "", null);

        assertThatThrownBy(() -> snippetService.create(request, "9.9.9.9", owner(9L)))
                .isInstanceOf(RateLimitExceededException.class);
        verify(snippetRepository, never()).save(any());
    }

    @Test
    void createEnforcesHourlyIpLimit() {
        when(rateLimiter.tryAcquire("snippet:user:9", 60)).thenReturn(true);
        when(rateLimiter.tryAcquire("snippet:ip:9.9.9.9", 20)).thenReturn(false);
        when(rateLimiter.retryAfterSeconds("snippet:ip:9.9.9.9")).thenReturn(30L);

        CreateSnippetRequest request = new CreateSnippetRequest("python", "print(1)", "", null);

        assertThatThrownBy(() -> snippetService.create(request, "9.9.9.9", owner(9L)))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("network");
        verify(snippetRepository, never()).save(any());
    }

    @Test
    void createSkipsRateLimitForAdmins() {
        when(slugGenerator.generate(8)).thenReturn("admin001");
        when(snippetRepository.existsBySlug("admin001")).thenReturn(false);
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateSnippetRequest request = new CreateSnippetRequest("python", "print(1)", "", null);
        snippetService.create(request, "9.9.9.9", admin(1L));

        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt());
    }

    @Test
    void getBySlugIncrementsViewCount() {
        Snippet existing = existingSnippet("origslug", "python", "print(1)", "in");
        existing.setViewCount(3);
        when(snippetRepository.findBySlug("origslug")).thenReturn(Optional.of(existing));
        when(snippetRepository.incrementViewCount("origslug")).thenReturn(1);

        SnippetResponse response = snippetService.getBySlug("origslug", null);

        assertThat(response.getViewCount()).isEqualTo(4);
        assertThat(response.getLanguage()).isEqualTo("python");
        assertThat(response.getCode()).isEqualTo("print(1)");
        assertThat(response.getStdin()).isEqualTo("in");
        verify(snippetRepository).incrementViewCount("origslug");
    }

    @Test
    void getBySlugThrowsWhenMissing() {
        when(snippetRepository.findBySlug("missing1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> snippetService.getBySlug("missing1", null))
                .isInstanceOf(SnippetNotFoundException.class);
    }

    @Test
    void privateSnippetIsHiddenFromOtherUsers() {
        Snippet existing = existingSnippet("privslug", "python", "print(1)", "");
        existing.setVisibility(SnippetVisibility.PRIVATE);
        existing.setOwner(owner(1L));
        when(snippetRepository.findBySlug("privslug")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> snippetService.getBySlug("privslug", owner(2L)))
                .isInstanceOf(SnippetNotFoundException.class);
        assertThatThrownBy(() -> snippetService.getBySlug("privslug", null))
                .isInstanceOf(SnippetNotFoundException.class);
        verify(snippetRepository, never()).incrementViewCount(anyString());
    }

    @Test
    void privateSnippetIsVisibleToOwner() {
        Snippet existing = existingSnippet("privslug", "python", "secret", "");
        existing.setVisibility(SnippetVisibility.PRIVATE);
        existing.setOwner(owner(1L));
        when(snippetRepository.findBySlug("privslug")).thenReturn(Optional.of(existing));

        SnippetResponse response = snippetService.getBySlug("privslug", owner(1L));

        assertThat(response.getCode()).isEqualTo("secret");
        assertThat(response.isOwnedByMe()).isTrue();
        verify(snippetRepository, never()).incrementViewCount(anyString());
    }

    @Test
    void unlistedSnippetIsVisibleWithoutLogin() {
        Snippet existing = existingSnippet("unlisted1", "python", "print(1)", "");
        existing.setVisibility(SnippetVisibility.UNLISTED);
        existing.setOwner(owner(1L));
        existing.setViewCount(2);
        when(snippetRepository.findBySlug("unlisted1")).thenReturn(Optional.of(existing));
        when(snippetRepository.incrementViewCount("unlisted1")).thenReturn(1);

        SnippetResponse response = snippetService.getBySlug("unlisted1", null);

        assertThat(response.getVisibility()).isEqualTo(SnippetVisibility.UNLISTED);
        assertThat(response.getViewCount()).isEqualTo(3);
    }

    @Test
    void forkCreatesNewSnippetAndLeavesOriginalUnchanged() {
        Snippet original = existingSnippet("origslug", "python", "print('original')", "old-in");
        original.setTitle("Original");
        when(snippetRepository.findBySlug("origslug")).thenReturn(Optional.of(original));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(slugGenerator.generate(8)).thenReturn("forked01");
        when(snippetRepository.existsBySlug("forked01")).thenReturn(false);
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ForkSnippetRequest request = new ForkSnippetRequest();
        request.setLanguage("javascript");
        request.setCode("console.log('forked')");
        request.setStdin("new-in");
        request.setTitle("Forked");

        SnippetResponse response = snippetService.fork("origslug", request, "2.2.2.2", owner(8L));

        assertThat(response.getSlug()).isEqualTo("forked01");
        assertThat(response.getLanguage()).isEqualTo("javascript");
        assertThat(response.getCode()).isEqualTo("console.log('forked')");
        assertThat(response.getStdin()).isEqualTo("new-in");
        assertThat(response.getForkedFrom()).isEqualTo("origslug");

        assertThat(original.getLanguage()).isEqualTo("python");
        assertThat(original.getCode()).isEqualTo("print('original')");
        assertThat(original.getStdin()).isEqualTo("old-in");
        assertThat(original.getTitle()).isEqualTo("Original");

        ArgumentCaptor<Snippet> captor = ArgumentCaptor.forClass(Snippet.class);
        verify(snippetRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("forked01");
        assertThat(captor.getValue().getForkedFromSlug()).isEqualTo("origslug");
        verify(snippetRepository, never()).save(original);
    }

    @Test
    void forkCopiesOriginalWhenBodyOmitsFields() {
        Snippet original = existingSnippet("origslug", "ruby", "puts 1", "stdin");
        when(snippetRepository.findBySlug("origslug")).thenReturn(Optional.of(original));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(slugGenerator.generate(anyInt())).thenReturn("copied01");
        when(snippetRepository.existsBySlug(anyString())).thenReturn(false);
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SnippetResponse response = snippetService.fork("origslug", new ForkSnippetRequest(), "3.3.3.3", owner(3L));

        assertThat(response.getLanguage()).isEqualTo("ruby");
        assertThat(response.getCode()).isEqualTo("puts 1");
        assertThat(response.getStdin()).isEqualTo("stdin");
        assertThat(response.getForkedFrom()).isEqualTo("origslug");
    }

    @Test
    void ownerCanChangeVisibilityAndDelete() {
        User owner = owner(1L);
        Snippet existing = existingSnippet("myslug01", "python", "print(1)", "");
        existing.setOwner(owner);
        when(snippetRepository.findBySlug("myslug01")).thenReturn(Optional.of(existing));
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSnippetRequest request = new UpdateSnippetRequest();
        request.setVisibility(SnippetVisibility.PRIVATE);
        SnippetResponse updated = snippetService.update("myslug01", request, owner);

        assertThat(updated.getVisibility()).isEqualTo(SnippetVisibility.PRIVATE);
        snippetService.delete("myslug01", owner);
        verify(snippetRepository).delete(existing);
    }

    @Test
    void otherUserCannotModifyPublicSnippet() {
        Snippet existing = existingSnippet("pubslug1", "python", "print(1)", "");
        existing.setOwner(owner(1L));
        when(snippetRepository.findBySlug("pubslug1")).thenReturn(Optional.of(existing));

        UpdateSnippetRequest request = new UpdateSnippetRequest();
        request.setTitle("Hijack");

        assertThatThrownBy(() -> snippetService.update("pubslug1", request, owner(2L)))
                .isInstanceOf(ForbiddenException.class);
        verify(snippetRepository, never()).save(any());
    }

    @Test
    void listMineFiltersByQuery() {
        User owner = owner(4L);
        Snippet mine = existingSnippet("abc12345", "python", "print(1)", "");
        mine.setTitle("Hello");
        mine.setOwner(owner);
        when(snippetRepository.searchMine(eq(owner), eq(null), eq("%hello%"), any()))
                .thenReturn(List.of(mine));

        SnippetListResponse response = snippetService.listMine(owner, "Hello", null, "updatedAt", "desc");

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getSnippets()).extracting(item -> item.getSlug()).containsExactly("abc12345");
    }

    private static User owner(long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@localhost");
        user.setName("User " + id);
        user.setRole(UserRole.USER);
        return user;
    }

    private static User admin(long id) {
        User user = owner(id);
        user.setRole(UserRole.ADMIN);
        user.setEmail("admin" + id + "@localhost");
        return user;
    }

    private static Snippet existingSnippet(String slug, String language, String code, String stdin) {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        snippet.setSlug(slug);
        snippet.setLanguage(language);
        snippet.setCode(code);
        snippet.setStdin(stdin);
        snippet.setViewCount(0);
        snippet.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        snippet.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return snippet;
    }
}
