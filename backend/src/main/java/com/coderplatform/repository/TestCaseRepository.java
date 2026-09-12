package com.coderplatform.repository;

import com.coderplatform.model.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderBySortOrderAscIdAsc(Long problemId);

    List<TestCase> findByProblemIdAndSampleIsTrueOrderBySortOrderAscIdAsc(Long problemId);

    Optional<TestCase> findByIdAndProblemId(Long id, Long problemId);

    int countByProblemId(Long problemId);

    int countByProblemIdAndSampleIsTrue(Long problemId);

    void deleteByProblemId(Long problemId);
}
