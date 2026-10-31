package com.techblog.platform.repository;

import com.techblog.platform.entity.ArticleReview;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleReviewRepository extends JpaRepository<ArticleReview, Long> {
    @EntityGraph(attributePaths = "actor")
    List<ArticleReview> findByArticleIdOrderByIdDesc(Long articleId);
}
