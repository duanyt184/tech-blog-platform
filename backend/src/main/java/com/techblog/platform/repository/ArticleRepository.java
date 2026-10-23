package com.techblog.platform.repository;

import com.techblog.platform.entity.Article;
import com.techblog.platform.entity.ArticleStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    List<Article> findByStatusOrderByCreatedAtDesc(ArticleStatus status);
}

