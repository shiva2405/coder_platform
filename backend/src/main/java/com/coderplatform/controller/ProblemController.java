package com.coderplatform.controller;

import com.coderplatform.model.JudgeResultResponse;
import com.coderplatform.model.ProblemDetailResponse;
import com.coderplatform.model.ProblemSummaryResponse;
import com.coderplatform.model.SubmissionSummaryResponse;
import com.coderplatform.model.SubmitCodeRequest;
import com.coderplatform.service.JudgeService;
import com.coderplatform.service.ProblemService;
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

import java.util.List;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private static final Logger logger = LoggerFactory.getLogger(ProblemController.class);

    private final ProblemService problemService;
    private final JudgeService judgeService;

    public ProblemController(ProblemService problemService, JudgeService judgeService) {
        this.problemService = problemService;
        this.judgeService = judgeService;
    }

    @GetMapping
    public ResponseEntity<List<ProblemSummaryResponse>> list() {
        return ResponseEntity.ok(problemService.listProblems());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ProblemDetailResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(problemService.getPublicProblem(slug));
    }

    @PostMapping("/{slug}/run-samples")
    public ResponseEntity<JudgeResultResponse> runSamples(
            @PathVariable String slug,
            @Valid @RequestBody SubmitCodeRequest request
    ) {
        logger.info("Running samples for {} in {}", slug, request.getLanguage());
        return ResponseEntity.ok(judgeService.runSamples(slug, request));
    }

    @PostMapping("/{slug}/submissions")
    public ResponseEntity<JudgeResultResponse> submit(
            @PathVariable String slug,
            @Valid @RequestBody SubmitCodeRequest request
    ) {
        logger.info("Submitting {} in {}", slug, request.getLanguage());
        return ResponseEntity.status(HttpStatus.CREATED).body(judgeService.submit(slug, request));
    }

    @GetMapping("/{slug}/submissions")
    public ResponseEntity<List<SubmissionSummaryResponse>> listSubmissions(@PathVariable String slug) {
        return ResponseEntity.ok(judgeService.listSubmissions(slug));
    }
}
