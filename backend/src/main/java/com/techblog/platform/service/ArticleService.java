package com.techblog.platform.service;

import com.techblog.platform.dto.*;
import com.techblog.platform.entity.*;
import com.techblog.platform.exception.*;
import com.techblog.platform.repository.*;
import com.techblog.platform.security.UserPrincipal;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ArticleService {
    public static final List<String> CATEGORIES = List.of("前端开发", "后端开发", "工程实践", "数据库", "学习笔记");
    private final ArticleRepository articles;
    private final UserRepository users;
    private final ArticleReviewRepository reviews;
    private final ArticleBookmarkRepository bookmarks;

    public ArticleService(ArticleRepository articles, UserRepository users,
                          ArticleReviewRepository reviews, ArticleBookmarkRepository bookmarks) {
        this.articles = articles;
        this.users = users;
        this.reviews = reviews;
        this.bookmarks = bookmarks;
    }

    public PageResponse<ArticleResponse> list(UserPrincipal principal, String scope, String q,
            String category, String tag, String status, String sort, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || page > 10000) throw new BadRequestException("分页参数不合法");
        if (q.length() > 100 || tag.length() > 24) throw new BadRequestException("搜索条件过长");
        if (!category.isBlank() && !CATEGORIES.contains(category)) throw new BadRequestException("分类不存在");
        if (!List.of("latest", "popular").contains(sort)) throw new BadRequestException("排序方式不合法");
        if (!scope.equals("public")) requireUser(principal);
        if (scope.equals("editor")) requireEditor(principal);
        ArticleStatus requested = parseStatus(status);
        Specification<Article> spec = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.isFalse(root.get("deleted")));
            if (scope.equals("public") || scope.equals("bookmarks")) {
                conditions.add(cb.equal(root.get("status"), ArticleStatus.PUBLISHED));
            } else if (requested != null) {
                conditions.add(cb.equal(root.get("status"), requested));
            }
            if (scope.equals("mine")) conditions.add(cb.equal(root.get("author").get("id"), principal.getId()));
            if (scope.equals("editor")) {
                // Drafts have never been submitted and are private to their author.
                conditions.add(root.get("status").in(ArticleStatus.PENDING, ArticleStatus.PUBLISHED, ArticleStatus.REJECTED));
            }
            if (scope.equals("bookmarks")) {
                var sub = query.subquery(Long.class);
                var mark = sub.from(ArticleBookmark.class);
                sub.select(mark.get("id")).where(cb.equal(mark.get("article"), root), cb.equal(mark.get("user").get("id"), principal.getId()));
                conditions.add(cb.exists(sub));
            }
            if (!category.isBlank()) conditions.add(cb.equal(root.get("category"), category));
            if (!tag.isBlank()) conditions.add(cb.isMember(tag.toLowerCase(Locale.ROOT), root.get("tags")));
            if (!q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var tagSearch = query.subquery(Long.class);
                var tagged = tagSearch.from(Article.class);
                jakarta.persistence.criteria.Join<Article, String> tagValue = tagged.join("tags");
                tagSearch.select(tagged.get("id")).where(cb.equal(tagged.get("id"), root.get("id")),
                        cb.like(cb.lower(tagValue), pattern, '\\'));
                conditions.add(cb.or(cb.exists(tagSearch), cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("summary")), pattern, '\\'),
                        cb.like(cb.lower(root.get("author").get("username")), pattern, '\\'),
                        cb.like(cb.lower(root.get("content")), pattern, '\\')));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        String dateField = scope.equals("mine") || scope.equals("editor") ? "updatedAt" : "publishedAt";
        Sort order = sort.equals("popular") ? Sort.by(Sort.Direction.DESC, "viewCount", "publishedAt", "id")
                : Sort.by(Sort.Direction.DESC, dateField, "id");
        Page<Article> result = articles.findAll(spec, PageRequest.of(page, size, order));
        return new PageResponse<>(result.map(article -> response(article, false, false, principal)).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ArticleResponse published(Long id, UserPrincipal principal) {
        Article article = find(id);
        if (article.getStatus() != ArticleStatus.PUBLISHED) throw new NotFoundException("文章不存在或尚未发布");
        articles.incrementViews(id);
        // Do not dirty the entity with a read counter: that would overwrite concurrent increments.
        ArticleResponse data = response(article, true, false, principal);
        return new ArticleResponse(data.id(), data.authorId(), data.author(), data.title(), data.summary(), data.content(),
                data.contentFormat(),
                data.category(), data.tags(), data.status(), data.version(), data.viewCount() + 1,
                data.createdAt(), data.updatedAt(), data.publishedAt(), data.bookmarked(), null);
    }

    public ArticleResponse mine(Long id, UserPrincipal principal) {
        return response(owned(id, principal), true, true, principal);
    }

    public ArticleResponse editorial(Long id, UserPrincipal principal) {
        requireEditor(principal);
        Article article = find(id);
        if (article.getStatus() == ArticleStatus.DRAFT) throw new NotFoundException("该文章尚未投稿");
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse create(ArticleWriteRequest request, UserPrincipal principal) {
        requireUser(principal);
        Article article = Article.draft(users.getReferenceById(principal.getId()));
        apply(article, request);
        article = articles.saveAndFlush(article);
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse update(Long id, ArticleWriteRequest request, UserPrincipal principal) {
        Article article = owned(id, principal);
        checkVersion(article, request.version());
        requireEditable(article);
        apply(article, request);
        articles.flush();
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse submit(Long id, Long version, UserPrincipal principal) {
        Article article = owned(id, principal);
        checkVersion(article, version);
        requireEditable(article);
        if (article.getContent().isBlank()) throw new BadRequestException("投稿前请填写正文");
        article.setStatus(ArticleStatus.PENDING);
        record(article, principal, "SUBMITTED", "提交审核");
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse withdraw(Long id, Long version, UserPrincipal principal) {
        Article article = owned(id, principal);
        checkVersion(article, version);
        if (article.getStatus() != ArticleStatus.PENDING && article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new ConflictException("只有待审核或已发布文章可以撤回");
        }
        article.setStatus(ArticleStatus.DRAFT);
        article.setPublishedAt(null);
        record(article, principal, "WITHDRAWN", "作者撤回为草稿");
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse review(Long id, ArticleReviewRequest request, UserPrincipal principal) {
        requireEditor(principal);
        Article article = find(id);
        if (article.getAuthor().getId().equals(principal.getId())) throw new AccessDeniedException("不能审核自己的文章");
        checkVersion(article, request.version());
        if (article.getStatus() != ArticleStatus.PENDING) throw new ConflictException("该文章已处理或被作者撤回，请刷新");
        String comment = request.comment() == null ? "" : request.comment().trim();
        if (request.decision() == ArticleReviewRequest.Decision.REJECT && comment.isEmpty()) {
            throw new BadRequestException("退回文章必须填写修改意见");
        }
        if (request.decision() == ArticleReviewRequest.Decision.APPROVE) {
            article.setStatus(ArticleStatus.PUBLISHED);
            article.setPublishedAt(LocalDateTime.now());
        } else {
            article.setStatus(ArticleStatus.REJECTED);
            article.setPublishedAt(null);
        }
        record(article, principal, article.getStatus().name(), comment);
        return response(article, true, true, principal);
    }

    @Transactional
    public void delete(Long id, Long version, UserPrincipal principal) {
        Article article = owned(id, principal);
        checkVersion(article, version);
        requireEditable(article);
        article.setDeleted(true);
        articles.flush();
    }

    @Transactional
    public void bookmark(Long id, UserPrincipal principal) {
        requireUser(principal);
        Article article = find(id);
        if (article.getStatus() != ArticleStatus.PUBLISHED) throw new NotFoundException("文章不存在或尚未发布");
        bookmarks.add(principal.getId(), id);
    }

    @Transactional
    public void unbookmark(Long id, UserPrincipal principal) {
        requireUser(principal);
        bookmarks.deleteByUserIdAndArticleId(principal.getId(), id);
    }

    private void apply(Article article, ArticleWriteRequest request) {
        if (!CATEGORIES.contains(request.category())) throw new BadRequestException("请选择有效分类");
        article.setTitle(request.title().trim());
        article.setSummary(request.summary() == null ? "" : request.summary().trim());
        article.setContent(request.content());
        // Older clients may omit the format. Preserve it when editing an existing article.
        if (request.contentFormat() != null) article.setContentFormat(request.contentFormat());
        article.setCategory(request.category());
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        request.tags().forEach(tag -> tags.add(tag.trim().toLowerCase(Locale.ROOT)));
        article.setTags(tags);
    }

    private void record(Article article, UserPrincipal principal, String action, String comment) {
        articles.flush();
        reviews.saveAndFlush(new ArticleReview(article, users.getReferenceById(principal.getId()), action, comment));
    }

    private Article find(Long id) {
        return articles.findByIdAndDeletedFalse(id).orElseThrow(() -> new NotFoundException("文章不存在"));
    }

    private Article owned(Long id, UserPrincipal principal) {
        requireUser(principal);
        Article article = find(id);
        if (!article.getAuthor().getId().equals(principal.getId())) throw new NotFoundException("文章不存在");
        return article;
    }

    private void requireEditable(Article article) {
        if (article.getStatus() != ArticleStatus.DRAFT && article.getStatus() != ArticleStatus.REJECTED) {
            throw new ConflictException("待审核或已发布文章需先撤回，才能修改或删除");
        }
    }

    private void checkVersion(Article article, Long version) {
        if (version == null) throw new BadRequestException("缺少文章版本，请刷新后重试");
        if (!article.getVersion().equals(version)) throw new ConflictException("文章已被更新，请刷新后重试，避免覆盖他人修改");
    }

    private void requireUser(UserPrincipal principal) {
        if (principal == null) throw new AuthenticationCredentialsNotFoundException("请先登录");
    }

    private void requireEditor(UserPrincipal principal) {
        requireUser(principal);
        if (principal.getRole() != UserRole.EDITOR && principal.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("没有审核权限");
        }
    }

    private ArticleStatus parseStatus(String status) {
        if (status.isBlank()) return null;
        try { return ArticleStatus.valueOf(status); }
        catch (IllegalArgumentException exception) { throw new BadRequestException("文章状态不合法"); }
    }

    private ArticleResponse response(Article article, boolean content, boolean history, UserPrincipal principal) {
        List<ArticleResponse.ReviewResponse> events = history ? reviews.findByArticleIdOrderByIdDesc(article.getId()).stream()
                .map(review -> new ArticleResponse.ReviewResponse(review.getId(), review.getActor().getUsername(),
                        review.getAction(), review.getComment(), review.getArticleVersion(), review.getCreatedAt())).toList() : null;
        Boolean bookmarked = content && principal != null
                ? bookmarks.existsByUserIdAndArticleId(principal.getId(), article.getId()) : null;
        return new ArticleResponse(article.getId(), article.getAuthor().getId(), article.getAuthor().getUsername(),
                article.getTitle(), article.getSummary(), content ? article.getContent() : null,
                article.getContentFormat(),
                article.getCategory(), article.getTags().stream().sorted().toList(), article.getStatus(), article.getVersion(),
                article.getViewCount(), article.getCreatedAt(), article.getUpdatedAt(), article.getPublishedAt(), bookmarked, events);
    }
}
