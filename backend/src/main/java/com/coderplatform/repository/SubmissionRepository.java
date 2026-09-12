package com.coderplatform.repository;

import com.coderplatform.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByProblemIdOrderByCreatedAtDesc(Long problemId);

    @Query("SELECT s FROM Submission s JOIN FETCH s.problem WHERE s.id = :id")
    Optional<Submission> findWithProblemById(@Param("id") Long id);

    @Query("SELECT DISTINCT s FROM Submission s JOIN FETCH s.problem LEFT JOIN FETCH s.caseResults WHERE s.id = :id")
    Optional<Submission> findDetailById(@Param("id") Long id);
}
