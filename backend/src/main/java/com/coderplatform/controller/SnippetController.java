package com.coderplatform.controller;

import com.coderplatform.model.CreateSnippetRequest;
import com.coderplatform.model.ForkSnippetRequest;
import com.coderplatform.model.SnippetResponse;
import com.coderplatform.service.SnippetService;
import com.coderplatform.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snippets")
public class SnippetController {

    private static final Logger logger = LoggerFactory.getLogger(SnippetController.class);

    private final SnippetService snippetService;

    public SnippetController(SnippetService snippetService) {
        this.snippetService = snippetService;
    }

    @PostMapping
    public ResponseEntity<SnippetResponse> create(
            @Valid @RequestBody CreateSnippetRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = ClientIpResolver.resolve(httpRequest);
        logger.info("Creating snippet for language {} from {}", request.getLanguage(), ip);
        SnippetResponse created = snippetService.create(request, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<SnippetResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(snippetService.getBySlug(slug));
    }

    @PostMapping("/{slug}/fork")
    public ResponseEntity<SnippetResponse> fork(
            @PathVariable String slug,
            @Valid @RequestBody(required = false) ForkSnippetRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = ClientIpResolver.resolve(httpRequest);
        logger.info("Forking snippet {} from {}", slug, ip);
        SnippetResponse created = snippetService.fork(slug, request, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
