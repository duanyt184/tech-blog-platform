package com.techblog.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.techblog.platform.entity.ReportStatus;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentReportResponse(
        Long id,
        Long commentId,
        Long articleId,
        String articleTitle,
        String commentAuthor,
        String commentContent,
        String reporter,
        String reason,
        ReportStatus status,
        LocalDateTime createdAt) {
}
