package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import com.coderplatform.exception.InvalidProblemException;
import com.coderplatform.exception.SubmissionNotFoundException;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.JudgeCaseResponse;
import com.coderplatform.model.JudgeResultResponse;
import com.coderplatform.model.Language;
import com.coderplatform.model.Problem;
import com.coderplatform.model.Submission;
import com.coderplatform.model.SubmissionCaseResult;
import com.coderplatform.model.SubmissionSummaryResponse;
import com.coderplatform.model.SubmitCodeRequest;
import com.coderplatform.model.TestCase;
import com.coderplatform.model.Verdict;
import com.coderplatform.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class JudgeService {

    private static final Logger logger = LoggerFactory.getLogger(JudgeService.class);

    private final ProblemService problemService;
    private final CodeExecutionService executionService;
    private final SubmissionRepository submissionRepository;
    private final SnippetConfig snippetConfig;

    public JudgeService(
            ProblemService problemService,
            CodeExecutionService executionService,
            SubmissionRepository submissionRepository,
            SnippetConfig snippetConfig
    ) {
        this.problemService = problemService;
        this.executionService = executionService;
        this.submissionRepository = submissionRepository;
        this.snippetConfig = snippetConfig;
    }

    public JudgeResultResponse runSamples(String slug, SubmitCodeRequest request) {
        Problem problem = problemService.findRequired(slug);
        List<TestCase> samples = problemService.loadTestCases(problem, true);
        if (samples.isEmpty()) {
            throw new InvalidProblemException("This problem has no sample test cases");
        }
        String language = normalizeLanguage(request.getLanguage());
        String code = validateCode(request.getCode());
        JudgeReport report = judge(problem, samples, language, code);
        return report.toResponse(null, problem.getSlug(), language, code);
    }

    public JudgeResultResponse submit(String slug, SubmitCodeRequest request) {
        Problem problem = problemService.findRequired(slug);
        List<TestCase> testCases = problemService.loadTestCases(problem, false);
        if (testCases.isEmpty()) {
            throw new InvalidProblemException("This problem has no test cases");
        }
        String language = normalizeLanguage(request.getLanguage());
        String code = validateCode(request.getCode());
        JudgeReport report = judge(problem, testCases, language, code);
        Submission saved = persist(problem, language, code, report);
        return JudgeResultResponse.fromSubmission(saved);
    }

    @Transactional(readOnly = true)
    public JudgeResultResponse getSubmission(Long id) {
        Submission submission = submissionRepository.findDetailById(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        return JudgeResultResponse.fromSubmission(submission);
    }

    @Transactional(readOnly = true)
    public List<SubmissionSummaryResponse> listSubmissions(String slug) {
        Problem problem = problemService.findRequired(slug);
        List<Submission> submissions = submissionRepository.findByProblemIdOrderByCreatedAtDesc(problem.getId());
        List<SubmissionSummaryResponse> result = new ArrayList<>();
        for (Submission submission : submissions) {
            result.add(SubmissionSummaryResponse.from(submission));
        }
        return result;
    }

    JudgeReport judge(Problem problem, List<TestCase> testCases, String language, String code) {
        int maxScore = 0;
        for (TestCase testCase : testCases) {
            maxScore += testCase.getPoints();
        }

        try (PreparedProgram program = executionService.prepare(language, code)) {
            CompileResult compileResult = program.compile();
            if (!compileResult.isSuccess()) {
                CodeExecutionResponse error = compileResult.getErrorResponse();
                Verdict verdict = error != null
                        ? Verdict.fromExecution(error.getStatus())
                        : Verdict.COMPILE_ERROR;
                if (verdict == Verdict.ACCEPTED) {
                    verdict = Verdict.COMPILE_ERROR;
                }
                String message = error != null && error.getError() != null ? error.getError() : "Compilation failed";
                long runtime = error != null ? error.getExecutionTime() : 0;
                logger.info("Compile failed for {} on {}: {}", language, problem.getSlug(), verdict);
                return JudgeReport.compileFailure(verdict, runtime, testCases.size(), maxScore, message);
            }

            List<CaseOutcome> outcomes = new ArrayList<>();
            Verdict overall = Verdict.ACCEPTED;
            int passed = 0;
            int score = 0;
            long totalRuntime = 0;
            int index = 1;

            for (TestCase testCase : testCases) {
                CodeExecutionResponse run = program.run(
                        testCase.getInput(),
                        problem.getTimeLimitMs(),
                        problem.getMemoryLimitBytes()
                );
                CaseOutcome outcome = evaluate(testCase, run, index++);
                outcomes.add(outcome);
                totalRuntime += outcome.runtimeMs;
                if (outcome.verdict == Verdict.ACCEPTED) {
                    passed++;
                    score += testCase.getPoints();
                } else if (overall == Verdict.ACCEPTED) {
                    overall = outcome.verdict;
                }
            }

            logger.info(
                    "Judged {} on {} as {} ({}/{})",
                    language,
                    problem.getSlug(),
                    overall,
                    passed,
                    testCases.size()
            );
            return new JudgeReport(overall, totalRuntime, passed, testCases.size(), score, maxScore, null, outcomes);
        }
    }

    private CaseOutcome evaluate(TestCase testCase, CodeExecutionResponse run, int index) {
        Verdict verdict = Verdict.fromExecution(run.getStatus());
        String actual = run.getOutput() != null ? run.getOutput() : "";
        String error = run.getError() != null ? run.getError() : "";

        if (run.getStatus() == CodeExecutionResponse.Status.SUCCESS) {
            if (OutputComparator.matches(testCase.getExpectedOutput(), actual)) {
                verdict = Verdict.ACCEPTED;
            } else {
                verdict = Verdict.WRONG_ANSWER;
            }
        }

        return new CaseOutcome(
                index,
                testCase,
                verdict,
                run.getExecutionTime(),
                testCase.getPoints(),
                testCase.getInput(),
                testCase.getExpectedOutput(),
                actual,
                error.isBlank() ? null : error
        );
    }

    @Transactional
    protected Submission persist(Problem problem, String language, String code, JudgeReport report) {
        Submission submission = new Submission();
        submission.setProblem(problem);
        submission.setLanguage(language);
        submission.setCode(code);
        submission.setVerdict(report.verdict);
        submission.setRuntimeMs(report.runtimeMs);
        submission.setPassedCount(report.passedCount);
        submission.setTotalCount(report.totalCount);
        submission.setScore(report.score);
        submission.setMaxScore(report.maxScore);
        submission.setCompileError(report.compileError);

        List<SubmissionCaseResult> stored = new ArrayList<>();
        for (CaseOutcome outcome : report.outcomes) {
            SubmissionCaseResult row = new SubmissionCaseResult();
            row.setSubmission(submission);
            row.setTestCase(outcome.testCase);
            row.setSample(outcome.testCase.isSample());
            row.setVerdict(outcome.verdict);
            row.setRuntimeMs(outcome.runtimeMs);
            row.setPoints(outcome.verdict == Verdict.ACCEPTED ? outcome.testCase.getPoints() : 0);
            row.setSortOrder(outcome.index);
            if (outcome.testCase.isSample()) {
                row.setInput(outcome.input);
                row.setExpectedOutput(outcome.expectedOutput);
                row.setActualOutput(outcome.actualOutput);
                row.setError(outcome.error);
            }
            stored.add(row);
        }
        submission.setCaseResults(stored);
        Submission saved = submissionRepository.save(submission);
        saved.setProblem(problem);
        return saved;
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new InvalidProblemException("Language is required");
        }
        try {
            return Language.fromId(language.trim()).getId();
        } catch (IllegalArgumentException ex) {
            throw new InvalidProblemException("Unsupported language: " + language);
        }
    }

    private String validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidProblemException("Code is required");
        }
        int bytes = code.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > snippetConfig.getMaxCodeBytes()) {
            throw new InvalidProblemException("Code exceeds maximum size of 256KB");
        }
        return code;
    }

    static final class JudgeReport {
        final Verdict verdict;
        final long runtimeMs;
        final int passedCount;
        final int totalCount;
        final int score;
        final int maxScore;
        final String compileError;
        final List<CaseOutcome> outcomes;

        JudgeReport(
                Verdict verdict,
                long runtimeMs,
                int passedCount,
                int totalCount,
                int score,
                int maxScore,
                String compileError,
                List<CaseOutcome> outcomes
        ) {
            this.verdict = verdict;
            this.runtimeMs = runtimeMs;
            this.passedCount = passedCount;
            this.totalCount = totalCount;
            this.score = score;
            this.maxScore = maxScore;
            this.compileError = compileError;
            this.outcomes = outcomes;
        }

        static JudgeReport compileFailure(Verdict verdict, long runtimeMs, int totalCount, int maxScore, String error) {
            return new JudgeReport(verdict, runtimeMs, 0, totalCount, 0, maxScore, error, List.of());
        }

        JudgeResultResponse toResponse(Long id, String problemSlug, String language, String code) {
            JudgeResultResponse response = new JudgeResultResponse();
            response.setId(id);
            response.setProblemSlug(problemSlug);
            response.setLanguage(language);
            response.setCode(code);
            response.setVerdict(verdict);
            response.setRuntimeMs(runtimeMs);
            response.setPassedCount(passedCount);
            response.setTotalCount(totalCount);
            response.setScore(score);
            response.setMaxScore(maxScore);
            response.setCompileError(compileError);
            List<JudgeCaseResponse> cases = new ArrayList<>();
            for (CaseOutcome outcome : outcomes) {
                cases.add(JudgeCaseResponse.publicView(
                        outcome.index,
                        outcome.testCase.isSample(),
                        outcome.verdict,
                        outcome.runtimeMs,
                        outcome.verdict == Verdict.ACCEPTED ? outcome.testCase.getPoints() : 0,
                        outcome.input,
                        outcome.expectedOutput,
                        outcome.actualOutput,
                        outcome.error
                ));
            }
            response.setCases(cases);
            return response;
        }
    }

    static final class CaseOutcome {
        final int index;
        final TestCase testCase;
        final Verdict verdict;
        final long runtimeMs;
        final int points;
        final String input;
        final String expectedOutput;
        final String actualOutput;
        final String error;

        CaseOutcome(
                int index,
                TestCase testCase,
                Verdict verdict,
                long runtimeMs,
                int points,
                String input,
                String expectedOutput,
                String actualOutput,
                String error
        ) {
            this.index = index;
            this.testCase = testCase;
            this.verdict = verdict;
            this.runtimeMs = runtimeMs;
            this.points = points;
            this.input = input;
            this.expectedOutput = expectedOutput;
            this.actualOutput = actualOutput;
            this.error = error;
        }
    }
}
