package com.techblog.platform.controller;

import com.techblog.platform.dto.*;
import com.techblog.platform.entity.Category;
import com.techblog.platform.security.UserPrincipal;
import com.techblog.platform.service.*;
import com.techblog.platform.service.CommunityService.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CommunityController {

    private final CommunityService community;
    private final ArticleService articles;

    public CommunityController(
        CommunityService community,
        ArticleService articles
    ) {
        this.community = community;
        this.articles = articles;
    }

    @GetMapping("/profiles/{id}")
    public Profile profile(@PathVariable Long id) {
        return community.profile(id);
    }

    @GetMapping("/profiles/{id}/articles")
    public PageResponse<ArticleResponse> blog(
        @PathVariable Long id,
        @RequestParam(defaultValue = "0") int page,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        community.profile(id);
        return articles.list(p, "blog", "", "", "", "", "latest", page, 12, id);
    }

    @PutMapping("/my/profile")
    public Profile updateProfile(
        @AuthenticationPrincipal UserPrincipal p,
        @Valid @RequestBody ProfileWrite r
    ) {
        return community.updateProfile(p, r);
    }

    @GetMapping("/community/categories")
    public List<Category> categories() {
        return community.categories();
    }

    @GetMapping("/articles/{id}/likes")
    public Likes likes(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        return community.likes(id, p);
    }

    @PutMapping("/articles/{id}/likes")
    public Likes like(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        return community.like(id, p, true);
    }

    @DeleteMapping("/articles/{id}/likes")
    public Likes unlike(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        return community.like(id, p, false);
    }

    @GetMapping("/admin/users")
    public PageResponse<AdminUser> users(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "") String q
    ) {
        return community.users(page, q);
    }

    @PutMapping("/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUser(
        @PathVariable Long id,
        @Valid @RequestBody UserWrite r,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        community.updateUser(id, r, p);
    }

    @PostMapping("/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Category createCategory(@Valid @RequestBody CategoryWrite r) {
        return community.saveCategory(null, r);
    }

    @PutMapping("/admin/categories/{id}")
    public Category updateCategory(
        @PathVariable Long id,
        @Valid @RequestBody CategoryWrite r
    ) {
        return community.saveCategory(id, r);
    }

    @DeleteMapping("/admin/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        community.deleteCategory(id);
    }

    @DeleteMapping("/admin/articles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeArticle(
        @PathVariable Long id,
        @RequestParam Long version
    ) {
        community.removeArticle(id, version);
    }

    @GetMapping("/admin/comments")
    public PageResponse<AdminComment> comments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "") String q
    ) {
        return community.comments(page, q);
    }
}
