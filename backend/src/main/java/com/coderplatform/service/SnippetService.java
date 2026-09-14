package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import com.coderplatform.exception.ForbiddenException;
import com.coderplatform.exception.InvalidSnippetException;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.exception.SnippetNotFoundException;
import com.coderplatform.exception.UnauthorizedException;
import com.coderplatform.model.CreateSnippetRequest;
import com.coderplatform.model.ForkSnippetRequest;
import com.coderplatform.model.Language;
import com.coderplatform.model.Snippet;
import com.coderplatform.model.SnippetListResponse;
import com.coderplatform.model.SnippetResponse;
import com.coderplatform.model.SnippetSummaryResponse;
import com.coderplatform.model.SnippetVisibility;
import com.coderplatform.model.UpdateSnippetRequest;
import com.coderplatform.model.User;
import com.coderplatform.repository.SnippetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SnippetService {

    private static final Logger logger = LoggerFactory.getLogger(SnippetService.class);
    private static final int MAX_SLUG_ATTEMPTS = 8;
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "title", "viewCount", "language");

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
    public SnippetResponse create(CreateSnippetRequest request, String clientIp, User user) {
        User owner = requireUser(user);
        String language = normalizeLanguage(request.getLanguage());
        String code = request.getCode();
        String stdin = request.getStdin() != null ? request.getStdin() : "";
        String title = normalizeTitle(request.getTitle());
        SnippetVisibility visibility = request.getVisibility() != null
                ? request.getVisibility()
                : SnippetVisibility.PUBLIC;

        validateContent(language, code, stdin, title);
        acquireRateLimit(owner, clientIp);

        Snippet snippet = persist(language, code, stdin, title, visibility, owner, null);
        logger.info("Created snippet {} for user {} language {}", snippet.getSlug(), owner.getId(), snippet.getLanguage());
        return SnippetResponse.from(snippet, owner);
    }

    @Transactional
    public SnippetResponse getBySlug(String slug, User viewer) {
        Snippet snippet = findVisible(slug, viewer);
        if (!ownedBy(snippet, viewer)) {
            snippetRepository.incrementViewCount(slug);
            snippet.setViewCount(snippet.getViewCount() + 1);
        }
        return SnippetResponse.from(snippet, viewer);
    }

    @Transactional
    public SnippetResponse fork(String slug, ForkSnippetRequest request, String clientIp, User user) {
        User owner = requireUser(user);
        Snippet original = findVisible(slug, owner);
        ForkSnippetRequest body = request != null ? request : new ForkSnippetRequest();

        String language = isBlank(body.getLanguage())
                ? original.getLanguage()
                : normalizeLanguage(body.getLanguage());
        String code = body.getCode() != null ? body.getCode() : original.getCode();
        String stdin = body.getStdin() != null ? body.getStdin() : nullToEmpty(original.getStdin());
        String title = body.getTitle() != null ? normalizeTitle(body.getTitle()) : original.getTitle();
        SnippetVisibility visibility = body.getVisibility() != null
                ? body.getVisibility()
                : SnippetVisibility.PUBLIC;

        validateContent(language, code, stdin, title);
        acquireRateLimit(owner, clientIp);

        Snippet forked = persist(language, code, stdin, title, visibility, owner, original.getSlug());
        logger.info("Forked snippet {} from {} for user {}", forked.getSlug(), original.getSlug(), owner.getId());
        return SnippetResponse.from(forked, owner);
    }

    @Transactional
    public SnippetResponse update(String slug, UpdateSnippetRequest request, User user) {
        User owner = requireUser(user);
        Snippet snippet = requireOwned(slug, owner);
        UpdateSnippetRequest body = request != null ? request : new UpdateSnippetRequest();

        String language = body.getLanguage() != null ? normalizeLanguage(body.getLanguage()) : snippet.getLanguage();
        String code = body.getCode() != null ? body.getCode() : snippet.getCode();
        String stdin = body.getStdin() != null ? body.getStdin() : nullToEmpty(snippet.getStdin());
        String title = body.getTitle() != null ? normalizeTitle(body.getTitle()) : snippet.getTitle();
        SnippetVisibility visibility = body.getVisibility() != null ? body.getVisibility() : snippet.getVisibility();

        validateContent(language, code, stdin, title);
        snippet.setLanguage(language);
        snippet.setCode(code);
        snippet.setStdin(stdin);
        snippet.setTitle(title);
        snippet.setVisibility(visibility);
        return SnippetResponse.from(snippetRepository.save(snippet), owner);
    }

    @Transactional
    public void delete(String slug, User user) {
        User owner = requireUser(user);
        Snippet snippet = requireOwned(slug, owner);
        snippetRepository.delete(snippet);
    }

    @Transactional(readOnly = true)
    public SnippetListResponse listMine(User user, String query, SnippetVisibility visibility, String sort, String order) {
        User owner = requireUser(user);
        String normalizedQuery = normalizeSearch(query);
        Sort sortSpec = resolveSort(sort, order);
        List<Snippet> snippets = snippetRepository.searchMine(owner, visibility, normalizedQuery, sortSpec);
        List<SnippetSummaryResponse> items = snippets.stream().map(SnippetSummaryResponse::from).toList();
        return new SnippetListResponse(items, items.size());
    }

    private Snippet persist(
            String language,
            String code,
            String stdin,
            String title,
            SnippetVisibility visibility,
            User owner,
            String forkedFromSlug
    ) {
        Snippet snippet = new Snippet();
        snippet.setSlug(nextUniqueSlug());
        snippet.setLanguage(language);
        snippet.setCode(code);
        snippet.setStdin(stdin != null ? stdin : "");
        snippet.setTitle(title);
        snippet.setVisibility(visibility != null ? visibility : SnippetVisibility.PUBLIC);
        snippet.setOwner(owner);
        snippet.setForkedFromSlug(forkedFromSlug);
        snippet.setViewCount(0);
        return snippetRepository.save(snippet);
    }

    private Snippet findVisible(String slug, User viewer) {
        Snippet snippet = findRequired(slug);
        if (snippet.getVisibility() == SnippetVisibility.PRIVATE && !ownedBy(snippet, viewer)) {
            throw new SnippetNotFoundException(slug);
        }
        return snippet;
    }

    private Snippet requireOwned(String slug, User user) {
        Snippet snippet = findVisible(slug, user);
        if (!ownedBy(snippet, user)) {
            throw new ForbiddenException("You do not own this snippet");
        }
        return snippet;
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

    private void acquireRateLimit(User user, String clientIp) {
        if (user.isAdmin()) {
            return;
        }
        String userKey = "snippet:user:" + user.getId();
        int userLimit = config.getAuthenticatedRateLimitPerHour();
        if (!rateLimiter.tryAcquire(userKey, userLimit)) {
            throw new RateLimitExceededException(
                    "Snippet saves are limited to " + userLimit + " per hour.",
                    rateLimiter.retryAfterSeconds(userKey)
            );
        }
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        String ipKey = "snippet:ip:" + ip;
        int ipLimit = config.getRateLimitPerHour();
        if (!rateLimiter.tryAcquire(ipKey, ipLimit)) {
            throw new RateLimitExceededException(
                    "Snippet saves from this network are limited to " + ipLimit + " per hour.",
                    rateLimiter.retryAfterSeconds(ipKey)
            );
        }
    }

    private Sort resolveSort(String sort, String order) {
        String field = sort == null || sort.isBlank() ? "updatedAt" : sort.trim();
        if (!SORT_FIELDS.contains(field)) {
            field = "updatedAt";
        }
        boolean ascending = order != null && order.equalsIgnoreCase("asc");
        return ascending ? Sort.by(Sort.Direction.ASC, field) : Sort.by(Sort.Direction.DESC, field);
    }

    private static String normalizeSearch(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static User requireUser(User user) {
        if (user == null || user.getId() == null) {
            throw new UnauthorizedException();
        }
        return user;
    }

    private static boolean ownedBy(Snippet snippet, User viewer) {
        return snippet.getOwner() != null
                && viewer != null
                && snippet.getOwner().getId() != null
                && snippet.getOwner().getId().equals(viewer.getId());
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
