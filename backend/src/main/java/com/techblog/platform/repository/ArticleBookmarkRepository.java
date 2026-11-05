package com.techblog.platform.repository;

import com.techblog.platform.entity.ArticleBookmark;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ArticleBookmarkRepository extends JpaRepository<ArticleBookmark, Long> {
    boolean existsByUserIdAndArticleId(Long userId, Long articleId);

    // PUT is idempotent, including concurrent requests on MySQL and MariaDB.
    @Modifying
    @Query(value = "INSERT INTO article_bookmarks (user_id, article_id, created_at) VALUES (:userId, :articleId, CURRENT_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE article_id = VALUES(article_id)", nativeQuery = true)
    void add(@Param("userId") Long userId, @Param("articleId") Long articleId);

    @Modifying
    @Query("delete from ArticleBookmark b where b.user.id = :userId and b.article.id = :articleId")
    void remove(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /** 一次查出当前页文章里哪些已被该用户收藏，避免逐行查询。 */
    @Query("select b.article.id from ArticleBookmark b where b.user.id = :userId and b.article.id in :articleIds")
    List<Long> findArticleIds(@Param("userId") Long userId, @Param("articleIds") Collection<Long> articleIds);
}
