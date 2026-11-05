package com.techblog.platform.repository;

import com.techblog.platform.entity.ArticleComment;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleCommentRepository extends JpaRepository<ArticleComment, Long> {

    /** 详情页：某篇已发布文章的评论，按时间倒序。 */
    @Query("select c from ArticleComment c join fetch c.user where c.article.id = :articleId "
            + "and c.deleted = false and c.article.deleted = false "
            + "and c.article.status = com.techblog.platform.entity.ArticleStatus.PUBLISHED "
            + "order by c.createdAt desc, c.id desc")
    List<ArticleComment> findForArticle(@Param("articleId") Long articleId, Pageable pageable);

    /** 删除评论时需要整棵子树，所以不过滤 deleted。 */
    @Query("select c from ArticleComment c where c.article.id = :articleId")
    List<ArticleComment> findAllByArticleId(@Param("articleId") Long articleId);

    /** 我发表的评论，附带文章标题用于「评论」页展示。 */
    @Query("select c from ArticleComment c join fetch c.article a join fetch c.user where c.user.id = :userId "
            + "and c.deleted = false and a.deleted = false "
            + "and a.status = com.techblog.platform.entity.ArticleStatus.PUBLISHED "
            + "order by c.createdAt desc, c.id desc")
    List<ArticleComment> findWritten(@Param("userId") Long userId, Pageable pageable);

    /** 别人对我的文章的评论；自己给自己的评论不算「收到的」。 */
    @Query("select c from ArticleComment c join fetch c.article a join fetch c.user where a.author.id = :authorId "
            + "and c.user.id <> :authorId and c.deleted = false and a.deleted = false "
            + "and a.status = com.techblog.platform.entity.ArticleStatus.PUBLISHED "
            + "order by c.createdAt desc, c.id desc")
    List<ArticleComment> findReceived(@Param("authorId") Long authorId, Pageable pageable);
}
