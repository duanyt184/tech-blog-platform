package com.techblog.platform.dto;

import com.techblog.platform.entity.Article;
import com.techblog.platform.entity.ArticleStatus;
import java.time.LocalDateTime;

/**
 * 文章对外响应对象（Issue #4 发布成功返回，后续 #3 列表/详情复用）。
 *
 * <p>不直接返回 JPA 实体，作者信息收窄成 {@link ArticleAuthorResponse}，
 * 避免序列化懒加载关系时触发代理异常或泄漏内部字段。</p>
 */
public record ArticleResponse(
        Long id,
        String title,
        String content,
        ArticleStatus status,
        Long viewCount,
        ArticleAuthorResponse author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime publishedAt) {

    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getStatus(),
                article.getViewCount(),
                ArticleAuthorResponse.from(article.getAuthor()),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                article.getPublishedAt());
    }
}
