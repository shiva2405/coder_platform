package com.coderplatform.controller;

import com.coderplatform.auth.CurrentUser;
import com.coderplatform.model.JudgeResultResponse;
import com.coderplatform.model.ProblemDetailResponse;
import com.coderplatform.model.ProblemSummaryResponse;
import com.coderplatform.model.SubmissionSummaryResponse;
import com.coderplatform.model.SubmitCodeRequest;
import com.coderplatform.model.WorkTicketResponse;
import com.coderplatform.service.ClientKey;
import com.coderplatform.service.ExecutionQuotaService;
import com.coderplatform.service.JudgeService;
import com.coderplatform.service.ProblemService;
import com.coderplatform.service.QueuedWorkService;
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

import java.util.List;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private static final Logger logger = LoggerFactory.getLogger(ProblemController.class);

    private final ProblemService problemService;
    private final JudgeService judgeService;
    private final QueuedWorkService queuedWorkService;
    private final ExecutionQuotaService quotaService;
    private final CurrentUser currentUser;

    public ProblemController(
            ProblemService problemService,
            JudgeService judgeService,
            QueuedWorkService queuedWorkService,
            ExecutionQuotaService quotaService,
            CurrentUser currentUser
    ) {
        this.problemService = problemService;
        this.judgeService = judgeService;
        this.queuedWorkService = queuedWorkService;
        this.quotaService = quotaService;
        this.currentUser = currentUser;
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
    public ResponseEntity<WorkTicketResponse> runSamples(
            @PathVariable String slug,
            @Valid @RequestBody SubmitCodeRequest request,
            HttpServletRequest httpRequest
    ) {
        logger.info("Running samples for {} in {}", slug, request.getLanguage());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(submitJudgeJob(
                request,
                httpRequest,
                () -> judgeService.runSamples(slug, request)
        ));
    }

    @PostMapping("/{slug}/submissions")
    public ResponseEntity<WorkTicketResponse> submit(
            @PathVariable String slug,
            @Valid @RequestBody SubmitCodeRequest request,
            HttpServletRequest httpRequest
    ) {
        logger.info("Submitting {} in {}", slug, request.getLanguage());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(submitJudgeJob(
                request,
                httpRequest,
                () -> judgeService.submit(slug, request)
        ));
    }

    @GetMapping("/{slug}/submissions")
    public ResponseEntity<List<SubmissionSummaryResponse>> listSubmissions(@PathVariable String slug) {
        return ResponseEntity.ok(judgeService.listSubmissions(slug));
    }

    private WorkTicketResponse submitJudgeJob(
            SubmitCodeRequest request,
            HttpServletRequest httpRequest,
            java.util.concurrent.Callable<JudgeResultResponse> work
    ) {
        String ip = ClientIpResolver.resolve(httpRequest);
        quotaService.consume(currentUser.optional().orElse(null), ip);
        return queuedWorkService.submit(
                request.getLanguage(),
                ClientKey.of(currentUser.optional().orElse(null), ip),
                work
        );
    }
}
