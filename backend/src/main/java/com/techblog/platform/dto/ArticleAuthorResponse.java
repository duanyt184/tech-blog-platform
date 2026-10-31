package com.techblog.platform.dto;

import com.techblog.platform.entity.User;

/**
 * 文章响应里附带的最小作者信息。
 *
 * <p>只暴露 id 和 username，刻意不包含 email、passwordHash 等字段，
 * 避免把用户隐私或密码哈希序列化给其他读者。</p>
 */
public record ArticleAuthorResponse(
        Long id,
        String username) {

    public static ArticleAuthorResponse from(User author) {
        return new ArticleAuthorResponse(author.getId(), author.getUsername());
    }
}
