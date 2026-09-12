package com.coderplatform.service;

import com.coderplatform.exception.InvalidProblemException;
import com.coderplatform.exception.ProblemNotFoundException;
import com.coderplatform.exception.TestCaseNotFoundException;
import com.coderplatform.model.AdminProblemResponse;
import com.coderplatform.model.CreateProblemRequest;
import com.coderplatform.model.CreateTestCaseRequest;
import com.coderplatform.model.Difficulty;
import com.coderplatform.model.Problem;
import com.coderplatform.model.ProblemDetailResponse;
import com.coderplatform.model.ProblemSummaryResponse;
import com.coderplatform.model.TagParser;
import com.coderplatform.model.TestCase;
import com.coderplatform.model.TestCaseAdminResponse;
import com.coderplatform.model.UpdateProblemRequest;
import com.coderplatform.repository.ProblemRepository;
import com.coderplatform.repository.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ProblemService {

    private static final Logger logger = LoggerFactory.getLogger(ProblemService.class);
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public ProblemService(ProblemRepository problemRepository, TestCaseRepository testCaseRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public List<ProblemSummaryResponse> listProblems() {
        List<Problem> problems = problemRepository.findAllByOrderByIdAsc();
        List<ProblemSummaryResponse> result = new ArrayList<>();
        for (Problem problem : problems) {
            int total = testCaseRepository.countByProblemId(problem.getId());
            int samples = testCaseRepository.countByProblemIdAndSampleIsTrue(problem.getId());
            result.add(ProblemSummaryResponse.from(problem, samples, total));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse getPublicProblem(String slug) {
        Problem problem = findRequired(slug);
        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderBySortOrderAscIdAsc(problem.getId());
        return ProblemDetailResponse.from(problem, testCases);
    }

    @Transactional(readOnly = true)
    public AdminProblemResponse getAdminProblem(String slug) {
        Problem problem = findRequired(slug);
        List<TestCase> testCases = testCaseRepository.findByProblemIdOrderBySortOrderAscIdAsc(problem.getId());
        return AdminProblemResponse.from(problem, testCases);
    }

    @Transactional(readOnly = true)
    public Problem findRequired(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ProblemNotFoundException("");
        }
        return problemRepository.findBySlug(slug.trim())
                .orElseThrow(() -> new ProblemNotFoundException(slug));
    }

    @Transactional(readOnly = true)
    public List<TestCase> loadTestCases(Problem problem, boolean samplesOnly) {
        if (samplesOnly) {
            return testCaseRepository.findByProblemIdAndSampleIsTrueOrderBySortOrderAscIdAsc(problem.getId());
        }
        return testCaseRepository.findByProblemIdOrderBySortOrderAscIdAsc(problem.getId());
    }

    @Transactional
    public AdminProblemResponse create(CreateProblemRequest request) {
        String slug = normalizeSlug(request.getSlug());
        if (problemRepository.existsBySlug(slug)) {
            throw new InvalidProblemException("Problem slug already exists: " + slug);
        }

        Problem problem = new Problem();
        problem.setSlug(slug);
        problem.setTitle(requireText(request.getTitle(), "Title is required"));
        problem.setDescription(requireText(request.getDescription(), "Description is required"));
        problem.setDifficulty(parseDifficulty(request.getDifficulty()));
        problem.setTags(TagParser.join(request.getTags()));
        problem.setTimeLimitMs(request.getTimeLimitMs());
        problem.setMemoryLimitBytes(request.getMemoryLimitBytes());
        problem = problemRepository.save(problem);

        List<CreateTestCaseRequest> cases = request.getTestCases() != null ? request.getTestCases() : List.of();
        int order = 1;
        for (CreateTestCaseRequest testCaseRequest : cases) {
            persistTestCase(problem, testCaseRequest, order++);
        }

        logger.info("Created problem {}", slug);
        return getAdminProblem(slug);
    }

    @Transactional
    public AdminProblemResponse update(String slug, UpdateProblemRequest request) {
        Problem problem = findRequired(slug);
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String nextSlug = normalizeSlug(request.getSlug());
            if (!nextSlug.equals(problem.getSlug()) && problemRepository.existsBySlug(nextSlug)) {
                throw new InvalidProblemException("Problem slug already exists: " + nextSlug);
            }
            problem.setSlug(nextSlug);
        }
        if (request.getTitle() != null) {
            problem.setTitle(requireText(request.getTitle(), "Title is required"));
        }
        if (request.getDescription() != null) {
            problem.setDescription(requireText(request.getDescription(), "Description is required"));
        }
        if (request.getDifficulty() != null) {
            problem.setDifficulty(parseDifficulty(request.getDifficulty()));
        }
        if (request.getTags() != null) {
            problem.setTags(TagParser.join(request.getTags()));
        }
        if (request.getTimeLimitMs() != null) {
            problem.setTimeLimitMs(request.getTimeLimitMs());
        }
        if (request.getMemoryLimitBytes() != null) {
            problem.setMemoryLimitBytes(request.getMemoryLimitBytes());
        }
        problemRepository.save(problem);
        return getAdminProblem(problem.getSlug());
    }

    @Transactional
    public void delete(String slug) {
        Problem problem = findRequired(slug);
        problemRepository.delete(problem);
        logger.info("Deleted problem {}", slug);
    }

    @Transactional
    public TestCaseAdminResponse addTestCase(String slug, CreateTestCaseRequest request) {
        Problem problem = findRequired(slug);
        int nextOrder = request.getSortOrder() != null
                ? request.getSortOrder()
                : testCaseRepository.countByProblemId(problem.getId()) + 1;
        TestCase saved = persistTestCase(problem, request, nextOrder);
        return TestCaseAdminResponse.from(saved);
    }

    @Transactional
    public TestCaseAdminResponse updateTestCase(String slug, Long testCaseId, CreateTestCaseRequest request) {
        Problem problem = findRequired(slug);
        TestCase testCase = testCaseRepository.findByIdAndProblemId(testCaseId, problem.getId())
                .orElseThrow(() -> new TestCaseNotFoundException(testCaseId));
        applyTestCase(testCase, request, request.getSortOrder() != null ? request.getSortOrder() : testCase.getSortOrder());
        return TestCaseAdminResponse.from(testCaseRepository.save(testCase));
    }

    @Transactional
    public void deleteTestCase(String slug, Long testCaseId) {
        Problem problem = findRequired(slug);
        TestCase testCase = testCaseRepository.findByIdAndProblemId(testCaseId, problem.getId())
                .orElseThrow(() -> new TestCaseNotFoundException(testCaseId));
        testCaseRepository.delete(testCase);
    }

    private TestCase persistTestCase(Problem problem, CreateTestCaseRequest request, int sortOrder) {
        TestCase testCase = new TestCase();
        testCase.setProblem(problem);
        applyTestCase(testCase, request, request.getSortOrder() != null ? request.getSortOrder() : sortOrder);
        return testCaseRepository.save(testCase);
    }

    private void applyTestCase(TestCase testCase, CreateTestCaseRequest request, int sortOrder) {
        testCase.setInput(request.getInput() != null ? request.getInput() : "");
        testCase.setExpectedOutput(request.getExpectedOutput() != null ? request.getExpectedOutput() : "");
        if (request.getPoints() < 0) {
            throw new InvalidProblemException("Points must be at least 0");
        }
        testCase.setPoints(request.getPoints());
        testCase.setSample(request.isSample());
        testCase.setSortOrder(sortOrder);
    }

    private String normalizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new InvalidProblemException("Slug is required");
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw new InvalidProblemException("Slug must be lowercase letters, numbers, and hyphens");
        }
        return normalized;
    }

    private Difficulty parseDifficulty(String value) {
        try {
            return Difficulty.from(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidProblemException(ex.getMessage());
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidProblemException(message);
        }
        return value.trim();
    }
}
