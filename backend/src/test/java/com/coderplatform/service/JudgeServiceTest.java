package com.coderplatform.service;

import com.coderplatform.config.SnippetConfig;
import com.coderplatform.exception.InvalidProblemException;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.Difficulty;
import com.coderplatform.model.JudgeCaseResponse;
import com.coderplatform.model.JudgeResultResponse;
import com.coderplatform.model.Problem;
import com.coderplatform.model.Submission;
import com.coderplatform.model.SubmitCodeRequest;
import com.coderplatform.model.TestCase;
import com.coderplatform.model.Verdict;
import com.coderplatform.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    @Mock
    private ProblemService problemService;

    @Mock
    private CodeExecutionService executionService;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private PreparedProgram preparedProgram;

    private JudgeService judgeService;

    @BeforeEach
    void setUp() {
        judgeService = new JudgeService(problemService, executionService, submissionRepository, new SnippetConfig());
    }

    @Test
    void compilesOnceThenRunsEveryTestCase() {
        Problem problem = problem("a-plus-b", 1000, 64 * 1024 * 1024);
        List<TestCase> cases = List.of(
                testCase(1L, "1 2", "3", 10, true, 1),
                testCase(2L, "0 0", "0", 20, false, 2),
                testCase(3L, "-5 10", "5", 30, false, 3)
        );
        when(problemService.findRequired("a-plus-b")).thenReturn(problem);
        when(problemService.loadTestCases(problem, false)).thenReturn(cases);
        when(executionService.prepare("python", "print(sum(map(int,input().split())))")).thenReturn(preparedProgram);
        when(preparedProgram.compile()).thenReturn(CompileResult.success());
        when(preparedProgram.run(eq("1 2"), eq(1000L), eq(64L * 1024 * 1024)))
                .thenReturn(CodeExecutionResponse.success("3\n", 4));
        when(preparedProgram.run(eq("0 0"), eq(1000L), eq(64L * 1024 * 1024)))
                .thenReturn(CodeExecutionResponse.success("0\n", 5));
        when(preparedProgram.run(eq("-5 10"), eq(1000L), eq(64L * 1024 * 1024)))
                .thenReturn(CodeExecutionResponse.success("5\n", 6));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            submission.setId(99L);
            return submission;
        });

        JudgeResultResponse response = judgeService.submit(
                "a-plus-b",
                new SubmitCodeRequest("python", "print(sum(map(int,input().split())))")
        );

        assertThat(response.getVerdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(response.getPassedCount()).isEqualTo(3);
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getScore()).isEqualTo(60);
        assertThat(response.getRuntimeMs()).isEqualTo(15);
        verify(preparedProgram, times(1)).compile();
        verify(preparedProgram, times(3)).run(anyString(), anyLong(), anyLong());
        verify(preparedProgram).close();
    }

    @Test
    void wrongAnswerDoesNotExposeHiddenInputOrOutput() {
        Problem problem = problem("a-plus-b", 1000, 64 * 1024 * 1024);
        TestCase sample = testCase(1L, "1 2", "3", 10, true, 1);
        TestCase hidden = testCase(2L, "secret-input", "99", 20, false, 2);
        when(problemService.findRequired("a-plus-b")).thenReturn(problem);
        when(problemService.loadTestCases(problem, false)).thenReturn(List.of(sample, hidden));
        when(executionService.prepare(anyString(), anyString())).thenReturn(preparedProgram);
        when(preparedProgram.compile()).thenReturn(CompileResult.success());
        when(preparedProgram.run(eq("1 2"), anyLong(), anyLong()))
                .thenReturn(CodeExecutionResponse.success("3", 2));
        when(preparedProgram.run(eq("secret-input"), anyLong(), anyLong()))
                .thenReturn(CodeExecutionResponse.success("0", 3));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JudgeResultResponse response = judgeService.submit("a-plus-b", new SubmitCodeRequest("python", "print(3)"));

        assertThat(response.getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(response.getPassedCount()).isEqualTo(1);
        JudgeCaseResponse hiddenResult = response.getCases().get(1);
        assertThat(hiddenResult.isSample()).isFalse();
        assertThat(hiddenResult.getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(hiddenResult.getInput()).isNull();
        assertThat(hiddenResult.getExpectedOutput()).isNull();
        assertThat(hiddenResult.getActualOutput()).isNull();

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        assertThat(captor.getValue().getCaseResults().get(1).getInput()).isNull();
        assertThat(captor.getValue().getCaseResults().get(1).getExpectedOutput()).isNull();
        assertThat(captor.getValue().getCaseResults().get(0).getInput()).isEqualTo("1 2");
        assertThat(captor.getValue().getCaseResults().get(0).getExpectedOutput()).isEqualTo("3");
        assertThat(captor.getValue().getCaseResults().get(0).getActualOutput()).isEqualTo("3");
    }

    @Test
    void mapsTimeoutAndRuntimeErrors() {
        Problem problem = problem("slow", 200, 64 * 1024 * 1024);
        TestCase sample = testCase(1L, "1", "1", 10, true, 1);
        when(problemService.findRequired("slow")).thenReturn(problem);
        when(problemService.loadTestCases(problem, true)).thenReturn(List.of(sample));
        when(executionService.prepare(anyString(), anyString())).thenReturn(preparedProgram);
        when(preparedProgram.compile()).thenReturn(CompileResult.success());
        when(preparedProgram.run(anyString(), eq(200L), anyLong()))
                .thenReturn(CodeExecutionResponse.timeout("", 200));

        JudgeResultResponse timeout = judgeService.runSamples("slow", new SubmitCodeRequest("python", "while True: pass"));
        assertThat(timeout.getVerdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
        assertThat(timeout.getCases().get(0).getInput()).isEqualTo("1");
        assertThat(timeout.getCases().get(0).getExpectedOutput()).isEqualTo("1");

        when(preparedProgram.run(anyString(), eq(200L), anyLong()))
                .thenReturn(CodeExecutionResponse.runtimeError("", "boom", 12));
        JudgeResultResponse runtime = judgeService.runSamples("slow", new SubmitCodeRequest("python", "raise SystemExit(1)"));
        assertThat(runtime.getVerdict()).isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    void compileErrorStopsBeforeAnyRun() {
        Problem problem = problem("a-plus-b", 1000, 64 * 1024 * 1024);
        when(problemService.findRequired("a-plus-b")).thenReturn(problem);
        when(problemService.loadTestCases(problem, false)).thenReturn(List.of(testCase(1L, "1 2", "3", 10, true, 1)));
        when(executionService.prepare(anyString(), anyString())).thenReturn(preparedProgram);
        when(preparedProgram.compile()).thenReturn(CompileResult.failure(
                CodeExecutionResponse.compileError("syntax error", 8)
        ));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JudgeResultResponse response = judgeService.submit("a-plus-b", new SubmitCodeRequest("python", "def"));

        assertThat(response.getVerdict()).isEqualTo(Verdict.COMPILE_ERROR);
        assertThat(response.getCompileError()).contains("syntax error");
        assertThat(response.getCases()).isEmpty();
        verify(preparedProgram, times(0)).run(anyString(), anyLong(), anyLong());
    }

    @Test
    void usesProblemTimeAndMemoryLimits() {
        Problem problem = problem("limits", 750, 32L * 1024 * 1024);
        when(problemService.findRequired("limits")).thenReturn(problem);
        when(problemService.loadTestCases(problem, true)).thenReturn(List.of(testCase(1L, "x", "x", 5, true, 1)));
        when(executionService.prepare(anyString(), anyString())).thenReturn(preparedProgram);
        when(preparedProgram.compile()).thenReturn(CompileResult.success());
        when(preparedProgram.run("x", 750, 32L * 1024 * 1024))
                .thenReturn(CodeExecutionResponse.success("x", 1));

        judgeService.runSamples("limits", new SubmitCodeRequest("python", "print(input())"));

        verify(preparedProgram).run("x", 750, 32L * 1024 * 1024);
    }

    @Test
    void rejectsUnknownLanguageBeforePrepare() {
        Problem problem = problem("a-plus-b", 1000, 64 * 1024 * 1024);
        when(problemService.findRequired("a-plus-b")).thenReturn(problem);
        when(problemService.loadTestCases(problem, true)).thenReturn(List.of(testCase(1L, "1", "1", 1, true, 1)));

        assertThatThrownBy(() -> judgeService.runSamples("a-plus-b", new SubmitCodeRequest("cobol", "DISPLAY 1.")))
                .isInstanceOf(InvalidProblemException.class)
                .hasMessageContaining("Unsupported language");
        verify(executionService, times(0)).prepare(anyString(), anyString());
    }

    @Test
    void compileIsInvokedOnceEvenWhenLaterCasesFail() {
        AtomicInteger compiles = new AtomicInteger();
        Problem problem = problem("a-plus-b", 1000, 64 * 1024 * 1024);
        when(problemService.findRequired("a-plus-b")).thenReturn(problem);
        when(problemService.loadTestCases(problem, false)).thenReturn(List.of(
                testCase(1L, "1", "1", 10, true, 1),
                testCase(2L, "2", "2", 10, false, 2)
        ));
        when(executionService.prepare(anyString(), anyString())).thenReturn(preparedProgram);
        when(preparedProgram.compile()).thenAnswer(invocation -> {
            compiles.incrementAndGet();
            return CompileResult.success();
        });
        when(preparedProgram.run(eq("1"), anyLong(), anyLong())).thenReturn(CodeExecutionResponse.success("1", 1));
        when(preparedProgram.run(eq("2"), anyLong(), anyLong()))
                .thenReturn(CodeExecutionResponse.memoryExceeded("", 3));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JudgeResultResponse response = judgeService.submit("a-plus-b", new SubmitCodeRequest("python", "print(1)"));

        assertThat(compiles.get()).isEqualTo(1);
        assertThat(response.getVerdict()).isEqualTo(Verdict.MEMORY_LIMIT_EXCEEDED);
        assertThat(response.getPassedCount()).isEqualTo(1);
    }

    private static Problem problem(String slug, int timeLimitMs, long memoryLimitBytes) {
        Problem problem = new Problem();
        problem.setId(1L);
        problem.setSlug(slug);
        problem.setTitle(slug);
        problem.setDescription("desc");
        problem.setDifficulty(Difficulty.EASY);
        problem.setTimeLimitMs(timeLimitMs);
        problem.setMemoryLimitBytes(memoryLimitBytes);
        return problem;
    }

    private static TestCase testCase(Long id, String input, String expected, int points, boolean sample, int order) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setInput(input);
        testCase.setExpectedOutput(expected);
        testCase.setPoints(points);
        testCase.setSample(sample);
        testCase.setSortOrder(order);
        return testCase;
    }
}
