package com.techblog.platform.dto;

import jakarta.validation.constraints.*;
import com.techblog.platform.entity.ContentFormat;
import java.util.List;

public record ArticleWriteRequest(
        @NotBlank(message = "请输入文章标题") @Size(max = 255) String title,
        @Size(max = 500) String summary,
        @NotNull @Size(max = 100000, message = "正文不能超过 100000 个字符") String content,
        ContentFormat contentFormat,
        @NotBlank @Size(max = 40) String category,
        @NotNull @Size(max = 5, message = "最多添加 5 个标签") List<@NotBlank @Size(max = 24) String> tags,
        @PositiveOrZero Long version) { }
