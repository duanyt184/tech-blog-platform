package com.techblog.platform.repository;

import com.techblog.platform.entity.ArticleBookmark;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ArticleBookmarkRepository extends JpaRepository<ArticleBookmark, Long> {
    boolean existsByUserIdAndArticleId(Long userId, Long articleId);
    void deleteByUserIdAndArticleId(Long userId, Long articleId);

    // PUT is idempotent, including concurrent requests on MySQL and MariaDB.
    @Modifying
    @Query(value = "INSERT INTO article_bookmarks (user_id, article_id, created_at) VALUES (:userId, :articleId, CURRENT_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE article_id = VALUES(article_id)", nativeQuery = true)
    void add(@Param("userId") Long userId, @Param("articleId") Long articleId);
}
