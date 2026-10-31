package com.techblog.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.techblog.platform.entity.ArticleStatus;
import com.techblog.platform.entity.ContentFormat;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArticleResponse(
        Long id, Long authorId, String author, String title, String summary, String content,
        ContentFormat contentFormat,
        String category, List<String> tags, ArticleStatus status, Long version, Long viewCount,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime publishedAt,
        Boolean bookmarked, List<ReviewResponse> reviews) {
    public record ReviewResponse(Long id, String actor, String action, String comment,
                                 Long articleVersion, LocalDateTime createdAt) { }
}
