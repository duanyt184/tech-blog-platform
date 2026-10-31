package com.techblog.platform.controller;

import com.techblog.platform.dto.ArticleResponse;
import com.techblog.platform.dto.CreateArticleRequest;
import com.techblog.platform.security.UserPrincipal;
import com.techblog.platform.service.ArticleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章接口（Issue #4 发布）。
 *
 * <pre>
 * POST /api/articles  201 {"id":...,"title":...,"content":...,"status":"PUBLISHED",...}   发布文章（需登录）
 * </pre>
 */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    /**
     * 发布纯文本文章。
     * 该接口在 SecurityConfig 里被「登录后才能访问」覆盖；
     * 当前用户来自认证主体 {@link UserPrincipal}，而不是请求体。
     */
    @PostMapping
    public ResponseEntity<ArticleResponse> publish(
            @Valid @RequestBody CreateArticleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ArticleResponse response = articleService.publish(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
