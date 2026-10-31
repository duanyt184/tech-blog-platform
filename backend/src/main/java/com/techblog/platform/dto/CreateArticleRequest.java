package com.techblog.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发布文章请求（Issue #4）。
 *
 * <p>验收标准要求「空内容不能提交」，所以标题和正文都做了 {@code @NotBlank} 校验：
 * 这里的 {@code @NotBlank} 会自动去掉首尾空白后再判断是否为空，全空格也会被拒绝。</p>
 */
public record CreateArticleRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 255, message = "标题不能超过 255 个字符")
        String title,

        @NotBlank(message = "正文不能为空")
        @Size(max = 20000, message = "正文不能超过 20000 个字符")
        String content) {
}
