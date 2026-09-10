package com.coderplatform.repository;

import com.coderplatform.model.Snippet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SnippetRepository extends JpaRepository<Snippet, Long> {

    Optional<Snippet> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Snippet s SET s.viewCount = s.viewCount + 1 WHERE s.slug = :slug")
    int incrementViewCount(@Param("slug") String slug);
}
