package com.techblog.platform.repository;

import com.techblog.platform.entity.Article;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {
    @Override
    @EntityGraph(attributePaths = "author")
    Page<Article> findAll(Specification<Article> spec, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<Article> findByIdAndDeletedFalse(Long id);

    // Views do not change the editorial version; parallel readers use an atomic increment.
    @Modifying
    @Query("update Article a set a.viewCount = a.viewCount + 1 where a.id = :id and a.deleted = false and a.status = com.techblog.platform.entity.ArticleStatus.PUBLISHED")
    int incrementViews(@Param("id") Long id);
}
