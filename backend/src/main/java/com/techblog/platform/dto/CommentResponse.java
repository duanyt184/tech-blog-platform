package com.techblog.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * @param articleTitle 仅在「评论」列表页返回；详情页省略
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentResponse(Long id, Long articleId, Long authorId, String articleTitle, String author,
                              String content, LocalDateTime createdAt, Long parentId) {
}
