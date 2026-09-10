package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import com.coderplatform.exception.InvalidSnippetException;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.exception.SnippetNotFoundException;
import com.coderplatform.model.CreateSnippetRequest;
import com.coderplatform.model.ForkSnippetRequest;
import com.coderplatform.model.Snippet;
import com.coderplatform.model.SnippetResponse;
import com.coderplatform.repository.SnippetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(rateLimiter.tryAcquire("1.1.1.1")).thenReturn(true);
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
        SnippetResponse response = snippetService.create(request, "1.1.1.1");

        assertThat(response.getSlug()).isEqualTo("abc12345");
        assertThat(response.getLanguage()).isEqualTo("python");
        assertThat(response.getCode()).isEqualTo("print(1)");
        assertThat(response.getStdin()).isEqualTo("hello");
        assertThat(response.getTitle()).isEqualTo("Demo");
        assertThat(response.getViewCount()).isZero();
        assertThat(response.getForkedFrom()).isNull();
    }

    @Test
    void createRejectsUnknownLanguage() {
        CreateSnippetRequest request = new CreateSnippetRequest("cobol", "DISPLAY 'HI'.", "", null);

        assertThatThrownBy(() -> snippetService.create(request, "1.1.1.1"))
                .isInstanceOf(InvalidSnippetException.class)
                .hasMessageContaining("Unsupported language");
        verify(snippetRepository, never()).save(any());
        verify(rateLimiter, never()).tryAcquire(anyString());
    }

    @Test
    void createRejectsCodeLargerThan256Kb() {
        String huge = "a".repeat(262145);
        CreateSnippetRequest request = new CreateSnippetRequest("python", huge, "", null);

        assertThatThrownBy(() -> snippetService.create(request, "1.1.1.1"))
                .isInstanceOf(InvalidSnippetException.class)
                .hasMessageContaining("256KB");
        verify(snippetRepository, never()).save(any());
    }

    @Test
    void createEnforcesHourlyIpLimit() {
        when(rateLimiter.tryAcquire("9.9.9.9")).thenReturn(false);
        when(rateLimiter.retryAfterSeconds("9.9.9.9")).thenReturn(1200L);

        CreateSnippetRequest request = new CreateSnippetRequest("python", "print(1)", "", null);

        assertThatThrownBy(() -> snippetService.create(request, "9.9.9.9"))
                .isInstanceOf(RateLimitExceededException.class);
        verify(snippetRepository, never()).save(any());
    }

    @Test
    void getBySlugIncrementsViewCount() {
        Snippet existing = existingSnippet("origslug", "python", "print(1)", "in");
        existing.setViewCount(3);
        when(snippetRepository.findBySlug("origslug")).thenReturn(Optional.of(existing));
        when(snippetRepository.incrementViewCount("origslug")).thenReturn(1);

        SnippetResponse response = snippetService.getBySlug("origslug");

        assertThat(response.getViewCount()).isEqualTo(4);
        assertThat(response.getLanguage()).isEqualTo("python");
        assertThat(response.getCode()).isEqualTo("print(1)");
        assertThat(response.getStdin()).isEqualTo("in");
        verify(snippetRepository).incrementViewCount("origslug");
    }

    @Test
    void getBySlugThrowsWhenMissing() {
        when(snippetRepository.findBySlug("missing1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> snippetService.getBySlug("missing1"))
                .isInstanceOf(SnippetNotFoundException.class);
    }

    @Test
    void forkCreatesNewSnippetAndLeavesOriginalUnchanged() {
        Snippet original = existingSnippet("origslug", "python", "print('original')", "old-in");
        original.setTitle("Original");
        when(snippetRepository.findBySlug("origslug")).thenReturn(Optional.of(original));
        when(rateLimiter.tryAcquire("2.2.2.2")).thenReturn(true);
        when(slugGenerator.generate(8)).thenReturn("forked01");
        when(snippetRepository.existsBySlug("forked01")).thenReturn(false);
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ForkSnippetRequest request = new ForkSnippetRequest();
        request.setLanguage("javascript");
        request.setCode("console.log('forked')");
        request.setStdin("new-in");
        request.setTitle("Forked");

        SnippetResponse response = snippetService.fork("origslug", request, "2.2.2.2");

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
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(slugGenerator.generate(anyInt())).thenReturn("copied01");
        when(snippetRepository.existsBySlug(anyString())).thenReturn(false);
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SnippetResponse response = snippetService.fork("origslug", new ForkSnippetRequest(), "3.3.3.3");

        assertThat(response.getLanguage()).isEqualTo("ruby");
        assertThat(response.getCode()).isEqualTo("puts 1");
        assertThat(response.getStdin()).isEqualTo("stdin");
        assertThat(response.getForkedFrom()).isEqualTo("origslug");
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
