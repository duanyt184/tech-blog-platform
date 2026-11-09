package com.techblog.platform.service;

import com.techblog.platform.dto.PageResponse;
import com.techblog.platform.entity.*;
import com.techblog.platform.exception.*;
import com.techblog.platform.repository.*;
import com.techblog.platform.security.UserPrincipal;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommunityService {

    private final UserRepository users;
    private final CategoryRepository categories;
    private final ArticleRepository articles;
    private final ArticleCommentRepository comments;
    private final JdbcTemplate jdbc;

    public CommunityService(
        UserRepository users,
        CategoryRepository categories,
        ArticleRepository articles,
        ArticleCommentRepository comments,
        JdbcTemplate jdbc
    ) {
        this.users = users;
        this.categories = categories;
        this.articles = articles;
        this.comments = comments;
        this.jdbc = jdbc;
    }

    public record Profile(
        Long id,
        String username,
        String displayName,
        String bio,
        UserRole role,
        LocalDateTime joinedAt,
        long articleCount
    ) {}

    public record ProfileWrite(
        @NotNull @Size(max = 80) String displayName,
        @NotNull @Size(max = 500) String bio
    ) {}

    public record CategoryWrite(
        @NotBlank @Size(max = 40) String name,
        @NotNull @Size(max = 300) String description,
        @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
        Long parentId
    ) {}

    public record UserWrite(@NotNull UserRole role, @NotNull Boolean enabled) {}

    public record AdminUser(
        Long id,
        String username,
        String email,
        UserRole role,
        boolean enabled,
        LocalDateTime createdAt
    ) {}

    public record AdminComment(
        Long id,
        Long articleId,
        String title,
        String author,
        String content,
        LocalDateTime createdAt
    ) {}

    public record Likes(long count, boolean liked) {}

    public Profile profile(Long id) {
        User u = users
            .findById(id)
            .orElseThrow(() -> new NotFoundException("用户不存在"));
        return new Profile(
            u.getId(),
            u.getUsername(),
            u.getDisplayName(),
            u.getBio(),
            u.getRole(),
            u.getCreatedAt(),
            articles.count((r, q, c) ->
                c.and(
                    c.equal(r.get("author").get("id"), id),
                    c.isFalse(r.get("deleted")),
                    c.equal(r.get("status"), ArticleStatus.PUBLISHED)
                )
            )
        );
    }

    @Transactional
    public Profile updateProfile(UserPrincipal p, ProfileWrite request) {
        var u = users
            .findById(p.getId())
            .orElseThrow(() -> new NotFoundException("用户不存在"));
        u.setDisplayName(request.displayName().trim());
        u.setBio(request.bio().trim());
        return profile(u.getId());
    }

    public List<Category> categories() {
        return categories.findAllByOrderByIdAsc();
    }

    @Transactional
    public Category saveCategory(Long id, CategoryWrite request) {
        Category category =
            id == null
                ? new Category()
                : categories
                      .lockById(id)
                      .orElseThrow(() -> new NotFoundException("分类不存在"));
        String name = request.name().trim();
        if (
            id == null && categories.existsByName(name)
        ) throw new ConflictException("分类名称已存在");
        if (
            id != null && !category.getName().equals(name)
        ) throw new BadRequestException(
            "分类名称不可修改；可修改介绍和颜色，或新增分类"
        );
        if (request.parentId() != null) {
            var parent = categories
                .findById(request.parentId())
                .orElseThrow(() -> new BadRequestException("父分类不存在"));
            if (
                Objects.equals(id, request.parentId()) ||
                parent.getParentId() != null
            ) throw new BadRequestException("仅支持两级分类，不能循环嵌套");
            if (
                id != null &&
                categories
                    .findAllByOrderByIdAsc()
                    .stream()
                    .anyMatch(c -> Objects.equals(c.getParentId(), id))
            ) throw new BadRequestException("有子分类的分类不能移到其他分类下");
        }
        category.setParentId(request.parentId());
        category.setName(name);
        category.setDescription(request.description().trim());
        category.setColor(request.color());
        return categories.saveAndFlush(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categories
            .lockById(id)
            .orElseThrow(() -> new NotFoundException("分类不存在"));
        if (
            articles.count((r, q, c) ->
                c.and(
                    c.equal(r.get("category"), category.getName()),
                    c.isFalse(r.get("deleted"))
                )
            ) > 0
        ) throw new ConflictException(
            "分类仍有文章（包括草稿），请先调整文章分类"
        );
        if (
            categories
                .findAllByOrderByIdAsc()
                .stream()
                .anyMatch(c -> Objects.equals(c.getParentId(), id))
        ) throw new ConflictException("请先移除子分类");
        categories.delete(category);
    }

    public PageResponse<AdminUser> users(int page, String q) {
        validatePage(page, q);
        var result = users.findAll(
            (r, query, c) ->
                c.or(
                    c.like(c.lower(r.get("username")), pattern(q), '\\'),
                    c.like(c.lower(r.get("email")), pattern(q), '\\')
                ),
            PageRequest.of(page, 20, Sort.by("id").descending())
        );
        return new PageResponse<>(
            result
                .map(u ->
                    new AdminUser(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getRole(),
                        u.isEnabled(),
                        u.getCreatedAt()
                    )
                )
                .getContent(),
            page,
            20,
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Transactional
    public void updateUser(
        Long id,
        UserWrite request,
        UserPrincipal principal
    ) {
        if (id.equals(principal.getId())) throw new ConflictException(
            "不能修改自己的角色或停用自己的账号"
        );
        // Serialize administrator changes, preserving at least one active administrator.
        var admins = users.lockAdmins();
        var u = users
            .findById(id)
            .orElseThrow(() -> new NotFoundException("用户不存在"));
        if (
            u.getRole() == UserRole.ADMIN &&
            u.isEnabled() &&
            (request.role() != UserRole.ADMIN || !request.enabled()) &&
            admins.size() <= 1
        ) throw new ConflictException("必须保留至少一位启用的管理员");
        u.setRole(request.role());
        u.setEnabled(request.enabled());
        users.flush();
    }

    public PageResponse<AdminComment> comments(int page, String q) {
        validatePage(page, q);
        var result = comments.findAll(
            (r, query, c) ->
                c.and(
                    c.isFalse(r.get("deleted")),
                    c.isFalse(r.get("article").get("deleted")),
                    c.like(c.lower(r.get("content")), pattern(q), '\\')
                ),
            PageRequest.of(page, 20, Sort.by("id").descending())
        );
        var items = result
            .map(c ->
                new AdminComment(
                    c.getId(),
                    c.getArticle().getId(),
                    c.getArticle().getTitle(),
                    c.getUser().getUsername(),
                    c.getContent(),
                    c.getCreatedAt()
                )
            )
            .getContent();
        return new PageResponse<>(
            items,
            page,
            20,
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Transactional
    public void removeArticle(Long id, Long version) {
        var a = articles
            .findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new NotFoundException("文章不存在"));
        if (
            a.getStatus() == ArticleStatus.DRAFT
        ) throw new AccessDeniedException("草稿仅作者可管理");
        if (
            !Objects.equals(a.getVersion(), version)
        ) throw new ConflictException("文章已更新，请刷新后重试");
        a.setDeleted(true);
        articles.flush();
    }

    private void published(Long id) {
        var a = articles
            .findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new NotFoundException("文章不存在"));
        if (
            a.getStatus() != ArticleStatus.PUBLISHED
        ) throw new NotFoundException("文章尚未发布");
    }

    public Likes likes(Long id, UserPrincipal p) {
        published(id);
        long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM article_likes WHERE article_id=?",
            Long.class,
            id
        );
        boolean liked =
            p != null &&
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM article_likes WHERE article_id=? AND user_id=?",
                Long.class,
                id,
                p.getId()
            ) > 0;
        return new Likes(count, liked);
    }

    @Transactional
    public Likes like(Long id, UserPrincipal p, boolean value) {
        // Lock the article so concurrent like/unlike requests have a well-defined order.
        if (
            jdbc
                .queryForList(
                    "SELECT id FROM articles WHERE id=? AND deleted=FALSE AND status='PUBLISHED' FOR UPDATE",
                    id
                )
                .isEmpty()
        ) throw new NotFoundException("文章不存在或尚未发布");
        if (value) jdbc.update(
            "INSERT INTO article_likes(user_id,article_id) VALUES (?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",
            p.getId(),
            id
        );
        else jdbc.update(
            "DELETE FROM article_likes WHERE user_id=? AND article_id=?",
            p.getId(),
            id
        );
        return likes(id, p);
    }

    private void validatePage(int page, String q) {
        if (
            page < 0 || page > 10000 || q.length() > 100
        ) throw new BadRequestException("分页或搜索参数不合法");
    }

    private String pattern(String q) {
        return (
            "%" +
            q
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") +
            "%"
        );
    }
}
