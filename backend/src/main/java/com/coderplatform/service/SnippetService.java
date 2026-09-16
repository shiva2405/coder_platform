package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import com.coderplatform.exception.ForbiddenException;
import com.coderplatform.exception.InvalidProjectException;
import com.coderplatform.exception.InvalidSnippetException;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.exception.SnippetNotFoundException;
import com.coderplatform.exception.UnauthorizedException;
import com.coderplatform.model.CreateSnippetRequest;
import com.coderplatform.model.ForkSnippetRequest;
import com.coderplatform.model.Language;
import com.coderplatform.model.ProjectFile;
import com.coderplatform.model.Snippet;
import com.coderplatform.model.SnippetListResponse;
import com.coderplatform.model.SnippetResponse;
import com.coderplatform.model.SnippetSummaryResponse;
import com.coderplatform.model.SnippetVisibility;
import com.coderplatform.model.UpdateSnippetRequest;
import com.coderplatform.model.User;
import com.coderplatform.repository.SnippetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        ProjectSources project = resolveProject(language, request.getCode(), request.getFiles(), request.getEntrypoint());
        String stdin = request.getStdin() != null ? request.getStdin() : "";
        String title = normalizeTitle(request.getTitle());
        SnippetVisibility visibility = request.getVisibility() != null
                ? request.getVisibility()
                : SnippetVisibility.PUBLIC;

        validateStdinAndTitle(stdin, title);
        acquireRateLimit(owner, clientIp);

        Snippet snippet = persist(project, stdin, title, visibility, owner, null);
        logger.info("Created snippet {} for user {} language {}", snippet.getSlug(), owner.getId(), snippet.getLanguage());
        return toResponse(snippet, owner);
    }

    @Transactional
    public SnippetResponse getBySlug(String slug, User viewer) {
        Snippet snippet = findVisible(slug, viewer);
        if (!ownedBy(snippet, viewer)) {
            snippetRepository.incrementViewCount(slug);
            snippet.setViewCount(snippet.getViewCount() + 1);
        }
        return toResponse(snippet, viewer);
    }

    @Transactional
    public SnippetResponse fork(String slug, ForkSnippetRequest request, String clientIp, User user) {
        User owner = requireUser(user);
        Snippet original = findVisible(slug, owner);
        ForkSnippetRequest body = request != null ? request : new ForkSnippetRequest();

        String language = isBlank(body.getLanguage())
                ? original.getLanguage()
                : normalizeLanguage(body.getLanguage());
        ProjectSources originalProject = storedProject(original);
        ProjectSources project;
        if (body.getFiles() != null) {
            project = resolveProject(language, body.getCode(), body.getFiles(), body.getEntrypoint());
        } else if (body.getCode() != null && !language.equals(original.getLanguage())) {
            project = resolveProject(language, body.getCode(), null, null);
        } else if (body.getCode() != null) {
            List<ProjectFile> files = originalProject.getFiles().stream()
                    .map(file -> originalProject.getEntrypoint().equals(file.getPath())
                            ? new ProjectFile(file.getPath(), body.getCode())
                            : file)
                    .toList();
            project = resolveProject(language, body.getCode(), files, originalProject.getEntrypoint());
        } else {
            project = resolveProject(
                    language,
                    originalProject.entrypointContent(),
                    originalProject.getFiles(),
                    originalProject.getEntrypoint()
            );
        }
        String stdin = body.getStdin() != null ? body.getStdin() : nullToEmpty(original.getStdin());
        String title = body.getTitle() != null ? normalizeTitle(body.getTitle()) : original.getTitle();
        SnippetVisibility visibility = body.getVisibility() != null
                ? body.getVisibility()
                : SnippetVisibility.PUBLIC;

        validateStdinAndTitle(stdin, title);
        acquireRateLimit(owner, clientIp);

        Snippet forked = persist(project, stdin, title, visibility, owner, original.getSlug());
        logger.info("Forked snippet {} from {} for user {}", forked.getSlug(), original.getSlug(), owner.getId());
        return toResponse(forked, owner);
    }

    @Transactional
    public SnippetResponse update(String slug, UpdateSnippetRequest request, User user) {
        User owner = requireUser(user);
        Snippet snippet = requireOwned(slug, owner);
        UpdateSnippetRequest body = request != null ? request : new UpdateSnippetRequest();

        String language = body.getLanguage() != null ? normalizeLanguage(body.getLanguage()) : snippet.getLanguage();
        ProjectSources current = storedProject(snippet);
        List<ProjectFile> files = body.getFiles() != null ? body.getFiles() : current.getFiles();
        String entrypoint = body.getEntrypoint() != null ? body.getEntrypoint() : current.getEntrypoint();
        String code = body.getCode() != null ? body.getCode() : current.entrypointContent();
        if (body.getFiles() == null && body.getCode() != null) {
            files = current.getFiles().stream()
                    .map(file -> current.getEntrypoint().equals(file.getPath())
                            ? new ProjectFile(file.getPath(), body.getCode())
                            : file)
                    .toList();
            code = body.getCode();
        }
        ProjectSources project = resolveProject(language, code, files, entrypoint);
        String stdin = body.getStdin() != null ? body.getStdin() : nullToEmpty(snippet.getStdin());
        String title = body.getTitle() != null ? normalizeTitle(body.getTitle()) : snippet.getTitle();
        SnippetVisibility visibility = body.getVisibility() != null ? body.getVisibility() : snippet.getVisibility();

        validateStdinAndTitle(stdin, title);
        applyProject(snippet, project);
        snippet.setStdin(stdin);
        snippet.setTitle(title);
        snippet.setVisibility(visibility);
        return toResponse(snippetRepository.save(snippet), owner);
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
            ProjectSources project,
            String stdin,
            String title,
            SnippetVisibility visibility,
            User owner,
            String forkedFromSlug
    ) {
        Snippet snippet = new Snippet();
        snippet.setSlug(nextUniqueSlug());
        applyProject(snippet, project);
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
        resolveProject(language, code, null, null);
        validateStdinAndTitle(stdin, title);
    }

    private void validateStdinAndTitle(String stdin, String title) {
        if (stdin != null && byteLength(stdin) > config.getMaxCodeBytes()) {
            throw new InvalidSnippetException("Stdin exceeds maximum size of 256KB");
        }
        if (title != null && title.length() > config.getMaxTitleLength()) {
            throw new InvalidSnippetException("Title must be at most 200 characters");
        }
    }

    private ProjectSources resolveProject(String language, String code, List<ProjectFile> files, String entrypoint) {
        try {
            return ProjectSources.resolve(language, code, files, entrypoint);
        } catch (InvalidProjectException ex) {
            throw new InvalidSnippetException(ex.getMessage());
        }
    }

    private ProjectSources storedProject(Snippet snippet) {
        List<ProjectFile> files = readFiles(snippet.getFilesJson());
        try {
            return ProjectSources.resolve(snippet.getLanguage(), snippet.getCode(), files, snippet.getEntrypoint());
        } catch (InvalidProjectException | IllegalArgumentException ex) {
            return ProjectSources.resolve(snippet.getLanguage(), snippet.getCode(), null, null);
        }
    }

    private void applyProject(Snippet snippet, ProjectSources project) {
        snippet.setLanguage(project.getLanguage());
        snippet.setCode(project.entrypointContent());
        snippet.setEntrypoint(project.getEntrypoint());
        snippet.setFilesJson(writeFiles(project.getFiles()));
    }

    private SnippetResponse toResponse(Snippet snippet, User viewer) {
        SnippetResponse response = SnippetResponse.from(snippet, viewer);
        ProjectSources project = storedProject(snippet);
        response.setFiles(project.getFiles());
        response.setEntrypoint(project.getEntrypoint());
        response.setCode(project.entrypointContent());
        return response;
    }

    private List<ProjectFile> readFiles(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ProjectFile> files = objectMapper.readValue(json, new TypeReference<>() {});
            return files == null ? List.of() : files;
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse snippet files JSON", e);
            return List.of();
        }
    }

    private String writeFiles(List<ProjectFile> files) {
        try {
            return objectMapper.writeValueAsString(files);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize project files", e);
        }
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
