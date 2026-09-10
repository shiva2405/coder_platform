package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import com.coderplatform.exception.InvalidSnippetException;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.exception.SnippetNotFoundException;
import com.coderplatform.model.CreateSnippetRequest;
import com.coderplatform.model.ForkSnippetRequest;
import com.coderplatform.model.Language;
import com.coderplatform.model.Snippet;
import com.coderplatform.model.SnippetResponse;
import com.coderplatform.repository.SnippetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class SnippetService {

    private static final Logger logger = LoggerFactory.getLogger(SnippetService.class);
    private static final int MAX_SLUG_ATTEMPTS = 8;

    private final SnippetRepository snippetRepository;
    private final SlugGenerator slugGenerator;
    private final SnippetRateLimiter rateLimiter;
    private final SnippetConfig config;

    public SnippetService(
            SnippetRepository snippetRepository,
            SlugGenerator slugGenerator,
            SnippetRateLimiter rateLimiter,
            SnippetConfig config
    ) {
        this.snippetRepository = snippetRepository;
        this.slugGenerator = slugGenerator;
        this.rateLimiter = rateLimiter;
        this.config = config;
    }

    @Transactional
    public SnippetResponse create(CreateSnippetRequest request, String clientIp) {
        String language = normalizeLanguage(request.getLanguage());
        String code = request.getCode();
        String stdin = request.getStdin() != null ? request.getStdin() : "";
        String title = normalizeTitle(request.getTitle());

        validateContent(language, code, stdin, title);
        acquireRateLimit(clientIp);

        Snippet snippet = persist(language, code, stdin, title, null);
        logger.info("Created snippet {} for language {}", snippet.getSlug(), snippet.getLanguage());
        return SnippetResponse.from(snippet);
    }

    @Transactional
    public SnippetResponse getBySlug(String slug) {
        Snippet snippet = findRequired(slug);
        snippetRepository.incrementViewCount(slug);
        snippet.setViewCount(snippet.getViewCount() + 1);
        return SnippetResponse.from(snippet);
    }

    @Transactional
    public SnippetResponse fork(String slug, ForkSnippetRequest request, String clientIp) {
        Snippet original = findRequired(slug);
        ForkSnippetRequest body = request != null ? request : new ForkSnippetRequest();

        String language = isBlank(body.getLanguage())
                ? original.getLanguage()
                : normalizeLanguage(body.getLanguage());
        String code = body.getCode() != null ? body.getCode() : original.getCode();
        String stdin = body.getStdin() != null ? body.getStdin() : nullToEmpty(original.getStdin());
        String title = body.getTitle() != null ? normalizeTitle(body.getTitle()) : original.getTitle();

        validateContent(language, code, stdin, title);
        acquireRateLimit(clientIp);

        Snippet forked = persist(language, code, stdin, title, original.getSlug());
        logger.info("Forked snippet {} from {} for language {}", forked.getSlug(), original.getSlug(), forked.getLanguage());
        return SnippetResponse.from(forked);
    }

    private Snippet persist(String language, String code, String stdin, String title, String forkedFromSlug) {
        Snippet snippet = new Snippet();
        snippet.setSlug(nextUniqueSlug());
        snippet.setLanguage(language);
        snippet.setCode(code);
        snippet.setStdin(stdin != null ? stdin : "");
        snippet.setTitle(title);
        snippet.setForkedFromSlug(forkedFromSlug);
        snippet.setViewCount(0);
        return snippetRepository.save(snippet);
    }

    private Snippet findRequired(String slug) {
        if (isBlank(slug) || !slug.matches("[A-Za-z0-9_-]{4,16}")) {
            throw new SnippetNotFoundException(slug == null ? "" : slug);
        }
        return snippetRepository.findBySlug(slug)
                .orElseThrow(() -> new SnippetNotFoundException(slug));
    }

    private String nextUniqueSlug() {
        int length = config.getSlugLength();
        for (int attempt = 0; attempt < MAX_SLUG_ATTEMPTS; attempt++) {
            int slugLength = attempt < 5 ? length : length + 2;
            String slug = slugGenerator.generate(slugLength);
            if (!snippetRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new IllegalStateException("Unable to generate a unique snippet slug");
    }

    private void acquireRateLimit(String clientIp) {
        String ip = isBlank(clientIp) ? "unknown" : clientIp;
        if (!rateLimiter.tryAcquire(ip)) {
            throw new RateLimitExceededException(rateLimiter.retryAfterSeconds(ip));
        }
    }

    void validateContent(String language, String code, String stdin, String title) {
        if (isBlank(code)) {
            throw new InvalidSnippetException("Code is required");
        }
        int codeBytes = byteLength(code);
        if (codeBytes > config.getMaxCodeBytes()) {
            throw new InvalidSnippetException("Code exceeds maximum size of 256KB");
        }
        if (stdin != null && byteLength(stdin) > config.getMaxCodeBytes()) {
            throw new InvalidSnippetException("Stdin exceeds maximum size of 256KB");
        }
        if (title != null && title.length() > config.getMaxTitleLength()) {
            throw new InvalidSnippetException("Title must be at most 200 characters");
        }
        normalizeLanguage(language);
    }

    private String normalizeLanguage(String language) {
        if (isBlank(language)) {
            throw new InvalidSnippetException("Language is required");
        }
        try {
            return Language.fromId(language.trim()).getId();
        } catch (IllegalArgumentException ex) {
            throw new InvalidSnippetException("Unsupported language: " + language);
        }
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int byteLength(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
