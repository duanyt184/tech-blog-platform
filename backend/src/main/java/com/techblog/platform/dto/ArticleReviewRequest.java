package com.techblog.platform.dto;
import jakarta.validation.constraints.*;
public record ArticleReviewRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull Decision decision,
        @Size(max = 2000) String comment) {
    public enum Decision { APPROVE, REJECT }
}
