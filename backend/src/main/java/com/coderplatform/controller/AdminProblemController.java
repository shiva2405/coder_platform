package com.coderplatform.controller;

import com.coderplatform.model.AdminProblemResponse;
import com.coderplatform.model.CreateProblemRequest;
import com.coderplatform.model.CreateTestCaseRequest;
import com.coderplatform.model.ProblemSummaryResponse;
import com.coderplatform.model.TestCaseAdminResponse;
import com.coderplatform.model.UpdateProblemRequest;
import com.coderplatform.service.ProblemService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/problems")
public class AdminProblemController {

    private static final Logger logger = LoggerFactory.getLogger(AdminProblemController.class);

    private final ProblemService problemService;

    public AdminProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public ResponseEntity<List<ProblemSummaryResponse>> list() {
        return ResponseEntity.ok(problemService.listProblems());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<AdminProblemResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(problemService.getAdminProblem(slug));
    }

    @PostMapping
    public ResponseEntity<AdminProblemResponse> create(@Valid @RequestBody CreateProblemRequest request) {
        logger.info("Admin creating problem {}", request.getSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(problemService.create(request));
    }

    @PutMapping("/{slug}")
    public ResponseEntity<AdminProblemResponse> update(
            @PathVariable String slug,
            @Valid @RequestBody UpdateProblemRequest request
    ) {
        logger.info("Admin updating problem {}", slug);
        return ResponseEntity.ok(problemService.update(slug, request));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        logger.info("Admin deleting problem {}", slug);
        problemService.delete(slug);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{slug}/test-cases")
    public ResponseEntity<TestCaseAdminResponse> addTestCase(
            @PathVariable String slug,
            @Valid @RequestBody CreateTestCaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(problemService.addTestCase(slug, request));
    }

    @PutMapping("/{slug}/test-cases/{id}")
    public ResponseEntity<TestCaseAdminResponse> updateTestCase(
            @PathVariable String slug,
            @PathVariable Long id,
            @Valid @RequestBody CreateTestCaseRequest request
    ) {
        return ResponseEntity.ok(problemService.updateTestCase(slug, id, request));
    }

    @DeleteMapping("/{slug}/test-cases/{id}")
    public ResponseEntity<Void> deleteTestCase(@PathVariable String slug, @PathVariable Long id) {
        problemService.deleteTestCase(slug, id);
        return ResponseEntity.noContent().build();
    }
}
