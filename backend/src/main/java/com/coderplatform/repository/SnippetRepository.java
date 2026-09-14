package com.coderplatform.repository;

import com.coderplatform.model.Snippet;
import com.coderplatform.model.SnippetVisibility;
import com.coderplatform.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SnippetRepository extends JpaRepository<Snippet, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<Snippet> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Snippet s SET s.viewCount = s.viewCount + 1 WHERE s.slug = :slug")
    int incrementViewCount(@Param("slug") String slug);

    @Query("""
            SELECT s FROM Snippet s
            WHERE s.owner = :owner
              AND (:visibility IS NULL OR s.visibility = :visibility)
              AND (
                :query IS NULL
                OR LOWER(s.slug) LIKE :query
                OR LOWER(s.language) LIKE :query
                OR LOWER(COALESCE(s.title, '')) LIKE :query
              )
            """)
    List<Snippet> searchMine(
            @Param("owner") User owner,
            @Param("visibility") SnippetVisibility visibility,
            @Param("query") String query,
            Sort sort
    );
}
