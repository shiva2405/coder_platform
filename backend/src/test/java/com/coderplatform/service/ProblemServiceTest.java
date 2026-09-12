package com.coderplatform.service;

import com.coderplatform.exception.InvalidProblemException;
import com.coderplatform.exception.ProblemNotFoundException;
import com.coderplatform.model.CreateProblemRequest;
import com.coderplatform.model.CreateTestCaseRequest;
import com.coderplatform.model.Difficulty;
import com.coderplatform.model.Problem;
import com.coderplatform.model.ProblemDetailResponse;
import com.coderplatform.model.SampleCaseResponse;
import com.coderplatform.model.TestCase;
import com.coderplatform.repository.ProblemRepository;
import com.coderplatform.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    private ProblemService problemService;

    @BeforeEach
    void setUp() {
        problemService = new ProblemService(problemRepository, testCaseRepository);
    }

    @Test
    void publicProblemOmitsHiddenTestCases() {
        Problem problem = existingProblem();
        TestCase sample = hiddenOrSample(true, "1 2", "3");
        TestCase hidden = hiddenOrSample(false, "secret", "99");
        when(problemRepository.findBySlug("a-plus-b")).thenReturn(Optional.of(problem));
        when(testCaseRepository.findByProblemIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(sample, hidden));

        ProblemDetailResponse response = problemService.getPublicProblem("a-plus-b");

        assertThat(response.getSlug()).isEqualTo("a-plus-b");
        assertThat(response.getSamples()).hasSize(1);
        SampleCaseResponse sampleResponse = response.getSamples().get(0);
        assertThat(sampleResponse.getInput()).isEqualTo("1 2");
        assertThat(sampleResponse.getExpectedOutput()).isEqualTo("3");
        assertThat(response.getHiddenTestCount()).isEqualTo(1);
        assertThat(response.getSamples().stream().map(SampleCaseResponse::getInput))
                .doesNotContain("secret");
    }

    @Test
    void unknownProblemThrows() {
        when(problemRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> problemService.getPublicProblem("missing"))
                .isInstanceOf(ProblemNotFoundException.class);
    }

    @Test
    void createRejectsDuplicateSlug() {
        CreateProblemRequest request = new CreateProblemRequest();
        request.setSlug("a-plus-b");
        request.setTitle("A + B");
        request.setDescription("Add two numbers");
        request.setDifficulty("EASY");
        when(problemRepository.existsBySlug("a-plus-b")).thenReturn(true);

        assertThatThrownBy(() -> problemService.create(request))
                .isInstanceOf(InvalidProblemException.class)
                .hasMessageContaining("already exists");
        verify(problemRepository, never()).save(any());
    }

    @Test
    void createPersistsProblemAndTestCases() {
        CreateProblemRequest request = new CreateProblemRequest();
        request.setSlug("Two-Sum");
        request.setTitle("Two Sum");
        request.setDescription("Find two numbers");
        request.setDifficulty("medium");
        request.setTags(List.of("arrays", "hashmap"));
        request.setTimeLimitMs(1500);
        request.setMemoryLimitBytes(67_108_864);
        CreateTestCaseRequest sample = new CreateTestCaseRequest();
        sample.setInput("1 2");
        sample.setExpectedOutput("3");
        sample.setPoints(10);
        sample.setSample(true);
        request.setTestCases(List.of(sample));

        when(problemRepository.existsBySlug("two-sum")).thenReturn(false);
        when(problemRepository.save(any(Problem.class))).thenAnswer(invocation -> {
            Problem problem = invocation.getArgument(0);
            problem.setId(5L);
            return problem;
        });
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(problemRepository.findBySlug("two-sum")).thenAnswer(invocation -> {
            Problem problem = new Problem();
            problem.setId(5L);
            problem.setSlug("two-sum");
            problem.setTitle("Two Sum");
            problem.setDescription("Find two numbers");
            problem.setDifficulty(Difficulty.MEDIUM);
            problem.setTags("arrays,hashmap");
            problem.setTimeLimitMs(1500);
            problem.setMemoryLimitBytes(67_108_864);
            return Optional.of(problem);
        });
        when(testCaseRepository.findByProblemIdOrderBySortOrderAscIdAsc(5L)).thenReturn(List.of());

        var created = problemService.create(request);

        assertThat(created.getSlug()).isEqualTo("two-sum");
        assertThat(created.getDifficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(created.getTags()).containsExactly("arrays", "hashmap");
        verify(testCaseRepository).save(any(TestCase.class));
    }

    private static Problem existingProblem() {
        Problem problem = new Problem();
        problem.setId(1L);
        problem.setSlug("a-plus-b");
        problem.setTitle("A + B");
        problem.setDescription("Add two numbers");
        problem.setDifficulty(Difficulty.EASY);
        problem.setTags("math");
        problem.setTimeLimitMs(1000);
        problem.setMemoryLimitBytes(67_108_864);
        return problem;
    }

    private static TestCase hiddenOrSample(boolean sample, String input, String expected) {
        TestCase testCase = new TestCase();
        testCase.setSample(sample);
        testCase.setInput(input);
        testCase.setExpectedOutput(expected);
        testCase.setPoints(10);
        return testCase;
    }
}
