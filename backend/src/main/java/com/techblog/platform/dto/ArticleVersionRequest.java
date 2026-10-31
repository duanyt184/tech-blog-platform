package com.techblog.platform.dto;
import jakarta.validation.constraints.*;
public record ArticleVersionRequest(@NotNull @PositiveOrZero Long version) { }
