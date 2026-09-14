package com.coderplatform.controller;

import com.coderplatform.auth.CurrentUser;
import com.coderplatform.model.CreateSnippetRequest;
import com.coderplatform.model.ForkSnippetRequest;
import com.coderplatform.model.SnippetListResponse;
import com.coderplatform.model.SnippetResponse;
import com.coderplatform.model.SnippetVisibility;
import com.coderplatform.model.UpdateSnippetRequest;
import com.coderplatform.service.SnippetService;
import com.coderplatform.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SnippetController {

    private static final Logger logger = LoggerFactory.getLogger(SnippetController.class);

    private final SnippetService snippetService;
    private final CurrentUser currentUser;

    public SnippetController(SnippetService snippetService, CurrentUser currentUser) {
        this.snippetService = snippetService;
        this.currentUser = currentUser;
    }

    @PostMapping("/snippets")
    public ResponseEntity<SnippetResponse> create(
            @Valid @RequestBody CreateSnippetRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = ClientIpResolver.resolve(httpRequest);
        logger.info("Creating snippet for language {} from user {}", request.getLanguage(), currentUser.require().getId());
        SnippetResponse created = snippetService.create(request, ip, currentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/snippets/{slug}")
    public ResponseEntity<SnippetResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(snippetService.getBySlug(slug, currentUser.optional().orElse(null)));
    }

    @PostMapping("/snippets/{slug}/fork")
    public ResponseEntity<SnippetResponse> fork(
            @PathVariable String slug,
            @Valid @RequestBody(required = false) ForkSnippetRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = ClientIpResolver.resolve(httpRequest);
        logger.info("Forking snippet {} from user {}", slug, currentUser.require().getId());
        SnippetResponse created = snippetService.fork(slug, request, ip, currentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/snippets/{slug}")
    public ResponseEntity<SnippetResponse> update(
            @PathVariable String slug,
            @Valid @RequestBody UpdateSnippetRequest request
    ) {
        return ResponseEntity.ok(snippetService.update(slug, request, currentUser.require()));
    }

    @DeleteMapping("/snippets/{slug}")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        snippetService.delete(slug, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/snippets")
    public SnippetListResponse mine(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "visibility", required = false) SnippetVisibility visibility,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "order", required = false) String order
    ) {
        return snippetService.listMine(currentUser.require(), query, visibility, sort, order);
    }
}
