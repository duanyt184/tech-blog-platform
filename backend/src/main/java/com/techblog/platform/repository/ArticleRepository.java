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

    /**
     * 收藏数按关系表的实际行数回写。
     * 不用 +1/-1：MariaDB JDBC 默认 useAffectedRows=false，ON DUPLICATE KEY 的返回值
     * 是「匹配行数」而不是「变更行数」，靠返回值判断是否新增会重复计数。
     */
    @Modifying
    @Query(value = "UPDATE articles a SET a.bookmark_count = (SELECT COUNT(*) FROM article_bookmarks b WHERE b.article_id = a.id) WHERE a.id = :id", nativeQuery = true)
    void syncBookmarkCount(@Param("id") Long id);

    /** 每个分类下已发布、未删除的文章数量，按分类聚合一次查询完成，避免逐分类 count。 */
    @Query("select a.category, count(a) from Article a where a.deleted = false and a.status = com.techblog.platform.entity.ArticleStatus.PUBLISHED group by a.category")
    java.util.List<Object[]> countPublishedByCategory();
}
