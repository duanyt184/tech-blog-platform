package com.techblog.platform.controller;

import com.techblog.platform.dto.*;
import com.techblog.platform.security.UserPrincipal;
import com.techblog.platform.service.ArticleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ArticleController {
    private final ArticleService service;
    public ArticleController(ArticleService service) { this.service = service; }

    @GetMapping("/categories")
    public List<String> categories() { return ArticleService.CATEGORIES; }

    @GetMapping({"/articles", "/my/articles", "/editor/articles", "/my/bookmarks"})
    public PageResponse<ArticleResponse> list(
            jakarta.servlet.http.HttpServletRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String tag,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String scope = switch (request.getRequestURI().substring(request.getContextPath().length())) {
            case "/api/my/articles" -> "mine";
            case "/api/editor/articles" -> "editor";
            case "/api/my/bookmarks" -> "bookmarks";
            default -> "public";
        };
        return service.list(principal, scope, q, category, tag, status, sort, page, size);
    }

    @GetMapping("/articles/{id}")
    public ArticleResponse published(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.published(id, principal);
    }
    @GetMapping("/my/articles/{id}")
    public ArticleResponse mine(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.mine(id, principal);
    }
    @GetMapping("/editor/articles/{id}")
    public ArticleResponse editorial(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.editorial(id, principal);
    }
    @PostMapping("/my/articles") @ResponseStatus(HttpStatus.CREATED)
    public ArticleResponse create(@Valid @RequestBody ArticleWriteRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return service.create(request, principal);
    }
    @PutMapping("/my/articles/{id}")
    public ArticleResponse update(@PathVariable Long id, @Valid @RequestBody ArticleWriteRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return service.update(id, request, principal);
    }
    @PostMapping("/my/articles/{id}/submit")
    public ArticleResponse submit(@PathVariable Long id, @Valid @RequestBody ArticleVersionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return service.submit(id, request.version(), principal);
    }
    @PostMapping("/my/articles/{id}/withdraw")
    public ArticleResponse withdraw(@PathVariable Long id, @Valid @RequestBody ArticleVersionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return service.withdraw(id, request.version(), principal);
    }
    @DeleteMapping("/my/articles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam Long version, @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(id, version, principal);
    }
    @PostMapping("/editor/articles/{id}/review")
    public ArticleResponse review(@PathVariable Long id, @Valid @RequestBody ArticleReviewRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return service.review(id, request, principal);
    }
    @PutMapping("/my/bookmarks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bookmark(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) { service.bookmark(id, principal); }
    @DeleteMapping("/my/bookmarks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbookmark(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) { service.unbookmark(id, principal); }
}
