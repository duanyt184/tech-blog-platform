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

    private final ForumService forum;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final CategoryRepository categories;

    public List<String> categoryNames() {
        return categories
            .findAllByOrderByIdAsc()
            .stream()
            .map(Category::getName)
            .toList();
    }

    private final ArticleRepository articles;
    private final UserRepository users;
    private final ArticleReviewRepository reviews;
    private final ArticleBookmarkRepository bookmarks;

    public ArticleService(
        ArticleRepository articles,
        UserRepository users,
        ArticleReviewRepository reviews,
        ArticleBookmarkRepository bookmarks,
        CategoryRepository categories,
        ForumService forum,
        org.springframework.jdbc.core.JdbcTemplate jdbc
    ) {
        this.forum = forum;
        this.jdbc = jdbc;
        this.categories = categories;
        this.articles = articles;
        this.users = users;
        this.reviews = reviews;
        this.bookmarks = bookmarks;
    }

    public PageResponse<ArticleResponse> list(
        UserPrincipal principal,
        String scope,
        String q,
        String category,
        String tag,
        String status,
        String sort,
        int page,
        int size
    ) {
        return list(
            principal,
            scope,
            q,
            category,
            tag,
            status,
            sort,
            page,
            size,
            null
        );
    }

    public PageResponse<ArticleResponse> list(
        UserPrincipal principal,
        String scope,
        String q,
        String category,
        String tag,
        String status,
        String sort,
        int page,
        int size,
        Long authorId
    ) {
        return listAdvanced(
            principal,
            scope,
            q,
            category,
            tag,
            status,
            sort,
            page,
            size,
            authorId,
            "",
            "",
            "",
            "all"
        );
    }

    public PageResponse<ArticleResponse> listAdvanced(
        UserPrincipal principal,
        String scope,
        String q,
        String category,
        String tag,
        String status,
        String sort,
        int page,
        int size,
        Long authorId,
        String author,
        String after,
        String before,
        String replies
    ) {
        if (
            !List.of("all", "yes", "no").contains(replies)
        ) throw new BadRequestException("回复筛选无效");
        java.time.LocalDate from = null,
            to = null;
        try {
            if (!after.isBlank()) from = java.time.LocalDate.parse(after);
            if (!before.isBlank()) to = java.time.LocalDate.parse(before);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BadRequestException("日期格式应为 YYYY-MM-DD");
        }
        if (
            from != null && to != null && from.isAfter(to)
        ) throw new BadRequestException("起始日期不能晚于结束日期");
        final var start = from;
        final var end = to;
        if (
            page < 0 || size < 1 || size > 50 || page > 10000
        ) throw new BadRequestException("分页参数不合法");
        if (
            q.length() > 100 || tag.length() > 24
        ) throw new BadRequestException("搜索条件过长");
        if (
            !category.isBlank() && !categories.existsByName(category)
        ) throw new BadRequestException("分类不存在");
        if (
            !List.of("latest", "popular", "newest").contains(sort)
        ) throw new BadRequestException("排序方式不合法");
        if (!scope.equals("public") && !scope.equals("blog")) requireUser(
            principal
        );
        if (scope.equals("editor")) requireEditor(principal);
        ArticleStatus requested = parseStatus(status);
        Specification<Article> spec = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.isFalse(root.get("deleted")));
            if (!author.isBlank()) conditions.add(
                cb.equal(
                    cb.lower(root.get("author").get("username")),
                    author.toLowerCase(Locale.ROOT)
                )
            );
            if (start != null) conditions.add(
                cb.greaterThanOrEqualTo(
                    root.get("publishedAt"),
                    start.atStartOfDay()
                )
            );
            if (end != null) conditions.add(
                cb.lessThan(
                    root.get("publishedAt"),
                    end.plusDays(1).atStartOfDay()
                )
            );
            if (!replies.equals("all")) {
                var sub = query.subquery(Long.class);
                var reply = sub.from(ArticleComment.class);
                sub.select(reply.get("id")).where(
                    cb.equal(reply.get("article"), root),
                    cb.isFalse(reply.get("deleted"))
                );
                conditions.add(
                    replies.equals("yes")
                        ? cb.exists(sub)
                        : cb.not(cb.exists(sub))
                );
            }
            if (scope.equals("public")) conditions.add(
                cb.isTrue(root.get("community"))
            );
            if (scope.equals("blog")) conditions.add(
                cb.equal(root.get("author").get("id"), authorId)
            );
            if (
                scope.equals("public") ||
                scope.equals("bookmarks") ||
                scope.equals("blog")
            ) {
                conditions.add(
                    cb.equal(root.get("status"), ArticleStatus.PUBLISHED)
                );
            } else if (requested != null) {
                conditions.add(cb.equal(root.get("status"), requested));
            }
            if (scope.equals("mine")) conditions.add(
                cb.equal(root.get("author").get("id"), principal.getId())
            );
            if (scope.equals("editor")) {
                // Drafts have never been submitted and are private to their author.
                conditions.add(
                    root
                        .get("status")
                        .in(
                            ArticleStatus.PENDING,
                            ArticleStatus.PUBLISHED,
                            ArticleStatus.REJECTED
                        )
                );
            }
            if (scope.equals("bookmarks")) {
                var sub = query.subquery(Long.class);
                var mark = sub.from(ArticleBookmark.class);
                sub.select(mark.get("id")).where(
                    cb.equal(mark.get("article"), root),
                    cb.equal(mark.get("user").get("id"), principal.getId())
                );
                conditions.add(cb.exists(sub));
            }
            if (!category.isBlank()) {
                var names = new ArrayList<String>();
                names.add(category);
                var parent = categories.findByName(category).orElseThrow();
                categories
                    .findAllByOrderByIdAsc()
                    .stream()
                    .filter(c ->
                        Objects.equals(c.getParentId(), parent.getId())
                    )
                    .forEach(c -> names.add(c.getName()));
                conditions.add(root.get("category").in(names));
            }
            if (!tag.isBlank()) conditions.add(
                cb.isMember(tag.toLowerCase(Locale.ROOT), root.get("tags"))
            );
            if (!q.isBlank()) {
                String pattern =
                    "%" +
                    q
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_") +
                    "%";
                var tagSearch = query.subquery(Long.class);
                var tagged = tagSearch.from(Article.class);
                jakarta.persistence.criteria.Join<Article, String> tagValue =
                    tagged.join("tags");
                tagSearch
                    .select(tagged.get("id"))
                    .where(
                        cb.equal(tagged.get("id"), root.get("id")),
                        cb.like(cb.lower(tagValue), pattern, '\\')
                    );
                var replySearch = query.subquery(Long.class);
                var searchedReply = replySearch.from(ArticleComment.class);
                replySearch
                    .select(searchedReply.get("id"))
                    .where(
                        cb.equal(searchedReply.get("article"), root),
                        cb.isFalse(searchedReply.get("deleted")),
                        cb.like(
                            cb.lower(searchedReply.get("content")),
                            pattern,
                            '\\'
                        )
                    );
                conditions.add(
                    cb.or(
                        cb.exists(replySearch),
                        cb.exists(tagSearch),
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("summary")), pattern, '\\'),
                        cb.like(
                            cb.lower(root.get("author").get("username")),
                            pattern,
                            '\\'
                        ),
                        cb.like(cb.lower(root.get("content")), pattern, '\\')
                    )
                );
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        String dateField =
            scope.equals("mine") || scope.equals("editor")
                ? "updatedAt"
                : sort.equals("newest")
                  ? "publishedAt"
                  : "updatedAt";
        // 热门按收藏数排序：收藏比阅读更能代表「值得读」，阅读数作为并列时的次级依据
        Sort order = sort.equals("popular")
            ? Sort.by(
                  Sort.Direction.DESC,
                  "bookmarkCount",
                  "viewCount",
                  "publishedAt",
                  "id"
              )
            : Sort.by(Sort.Direction.DESC, dateField, "id");
        Page<Article> result = articles.findAll(
            spec,
            PageRequest.of(page, size, order)
        );
        List<Article> items = result.getContent();
        Set<Long> bookmarkedIds = Set.of();
        if (principal != null && !items.isEmpty()) {
            bookmarkedIds = new HashSet<>(
                bookmarks.findArticleIds(
                    principal.getId(),
                    items.stream().map(Article::getId).toList()
                )
            );
        }
        final Set<Long> marked = bookmarkedIds;
        return new PageResponse<>(
            items
                .stream()
                .map(article ->
                    response(
                        article,
                        false,
                        false,
                        principal,
                        marked.contains(article.getId())
                    )
                )
                .toList(),
            page,
            size,
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    /** 公开侧边栏使用的分类统计：总数 + 每个分类的已发布文章数。 */
    public CategoryStatsResponse categoryStats() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : articles.countPublishedByCategory()) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<CategoryStatsResponse.CategoryCount> categories = categoryNames()
            .stream()
            .map(name ->
                new CategoryStatsResponse.CategoryCount(
                    name,
                    counts.getOrDefault(name, 0L)
                )
            )
            .toList();
        return new CategoryStatsResponse(total, categories);
    }

    @Transactional
    public ArticleResponse published(Long id, UserPrincipal principal) {
        Article article = find(id);
        if (
            article.getStatus() != ArticleStatus.PUBLISHED
        ) throw new NotFoundException("文章不存在或尚未发布");
        articles.incrementViews(id);
        // Do not dirty the entity with a read counter: that would overwrite concurrent increments.
        ArticleResponse data = response(article, true, false, principal);
        return new ArticleResponse(
            data.id(),
            data.authorId(),
            data.author(),
            data.title(),
            data.summary(),
            data.content(),
            data.contentFormat(),
            data.category(),
            data.tags(),
            data.status(),
            data.version(),
            data.viewCount() + 1,
            data.bookmarkCount(),
            data.createdAt(),
            data.updatedAt(),
            data.publishedAt(),
            data.bookmarked(),
            null,
            data.community(),
            data.replyCount(),
            data.likeCount()
        );
    }

    public ArticleResponse mine(Long id, UserPrincipal principal) {
        return response(owned(id, principal), true, true, principal);
    }

    public ArticleResponse editorial(Long id, UserPrincipal principal) {
        requireEditor(principal);
        Article article = find(id);
        if (
            article.getStatus() == ArticleStatus.DRAFT
        ) throw new NotFoundException("该文章尚未投稿");
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse create(
        ArticleWriteRequest request,
        UserPrincipal principal
    ) {
        requireUser(principal);
        Article article = Article.draft(
            users.getReferenceById(principal.getId())
        );
        apply(article, request);
        article = articles.saveAndFlush(article);
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse update(
        Long id,
        ArticleWriteRequest request,
        UserPrincipal principal
    ) {
        Article article = owned(id, principal);
        checkVersion(article, request.version());
        requireEditable(article);
        apply(article, request);
        articles.flush();
        if (article.getStatus() == ArticleStatus.PUBLISHED) forum.detect(
            article.getId(),
            null,
            article.getTitle() + "\n" + article.getContent()
        );
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse submit(
        Long id,
        Long version,
        UserPrincipal principal
    ) {
        return publishTopic(id, version, principal);
    }

    @Transactional
    public ArticleResponse publishTopic(
        Long id,
        Long version,
        UserPrincipal principal
    ) {
        Article article = owned(id, principal);
        checkVersion(article, version);
        if (article.getContent().isBlank()) throw new BadRequestException(
            "发布前请填写正文"
        );
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setCommunity(true);
        if (article.getPublishedAt() == null) article.setPublishedAt(
            LocalDateTime.now()
        );
        articles.flush();
        forum.detect(
            article.getId(),
            null,
            article.getTitle() + "\n" + article.getContent()
        );
        return response(article, true, false, principal);
    }

    @Transactional
    public ArticleResponse withdraw(
        Long id,
        Long version,
        UserPrincipal principal
    ) {
        Article article = owned(id, principal);
        checkVersion(article, version);
        if (
            article.getStatus() != ArticleStatus.PENDING &&
            article.getStatus() != ArticleStatus.PUBLISHED
        ) {
            throw new ConflictException("只有待审核或已发布文章可以撤回");
        }
        article.setStatus(ArticleStatus.DRAFT);
        article.setCommunity(false);
        article.setPublishedAt(null);
        record(article, principal, "WITHDRAWN", "作者撤回为草稿");
        return response(article, true, true, principal);
    }

    @Transactional
    public ArticleResponse review(
        Long id,
        ArticleReviewRequest request,
        UserPrincipal principal
    ) {
        requireEditor(principal);
        Article article = find(id);
        if (
            article.getAuthor().getId().equals(principal.getId())
        ) throw new AccessDeniedException("不能审核自己的文章");
        checkVersion(article, request.version());
        if (
            article.getStatus() != ArticleStatus.PENDING
        ) throw new ConflictException("该文章已处理或被作者撤回，请刷新");
        String comment =
            request.comment() == null ? "" : request.comment().trim();
        if (
            request.decision() == ArticleReviewRequest.Decision.REJECT &&
            comment.isEmpty()
        ) {
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
        if (
            article.getStatus() != ArticleStatus.PUBLISHED
        ) throw new NotFoundException("文章不存在或尚未发布");
        // 重复收藏是幂等的；计数按关系表回写，重复点击不会累加
        bookmarks.add(principal.getId(), id);
        articles.syncBookmarkCount(id);
    }

    @Transactional
    public void unbookmark(Long id, UserPrincipal principal) {
        requireUser(principal);
        bookmarks.remove(principal.getId(), id);
        articles.syncBookmarkCount(id);
    }

    private void apply(Article article, ArticleWriteRequest request) {
        if (
            categories.findByName(request.category()).isEmpty()
        ) throw new BadRequestException("请选择有效分类");
        article.setTitle(request.title().trim());
        article.setSummary(
            request.summary() == null ? "" : request.summary().trim()
        );
        article.setContent(request.content());
        // Older clients may omit the format. Preserve it when editing an existing article.
        if (request.contentFormat() != null) article.setContentFormat(
            request.contentFormat()
        );
        article.setCategory(request.category());
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        request
            .tags()
            .forEach(tag -> tags.add(tag.trim().toLowerCase(Locale.ROOT)));
        article.setTags(tags);
        tags.forEach(tag ->
            jdbc.update("INSERT IGNORE INTO forum_tags(name) VALUES(?)", tag)
        );
    }

    private void record(
        Article article,
        UserPrincipal principal,
        String action,
        String comment
    ) {
        articles.flush();
        reviews.saveAndFlush(
            new ArticleReview(
                article,
                users.getReferenceById(principal.getId()),
                action,
                comment
            )
        );
    }

    private Article find(Long id) {
        return articles
            .findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new NotFoundException("文章不存在"));
    }

    private Article owned(Long id, UserPrincipal principal) {
        requireUser(principal);
        Article article = find(id);
        if (
            !article.getAuthor().getId().equals(principal.getId())
        ) throw new NotFoundException("文章不存在");
        return article;
    }

    private void requireEditable(Article article) {
        // Authors may edit or delete their own published topics; version guards stale writes.
    }

    private void checkVersion(Article article, Long version) {
        if (version == null) throw new BadRequestException(
            "缺少文章版本，请刷新后重试"
        );
        if (!article.getVersion().equals(version)) throw new ConflictException(
            "文章已被更新，请刷新后重试，避免覆盖他人修改"
        );
    }

    private void requireUser(UserPrincipal principal) {
        if (
            principal == null
        ) throw new AuthenticationCredentialsNotFoundException("请先登录");
    }

    private void requireEditor(UserPrincipal principal) {
        requireUser(principal);
        if (
            principal.getRole() != UserRole.EDITOR &&
            principal.getRole() != UserRole.ADMIN
        ) {
            throw new AccessDeniedException("没有审核权限");
        }
    }

    private ArticleStatus parseStatus(String status) {
        if (status.isBlank()) return null;
        try {
            return ArticleStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("文章状态不合法");
        }
    }

    private ArticleResponse response(
        Article article,
        boolean content,
        boolean history,
        UserPrincipal principal
    ) {
        Boolean bookmarked =
            content && principal != null
                ? bookmarks.existsByUserIdAndArticleId(
                      principal.getId(),
                      article.getId()
                  )
                : null;
        return response(article, content, history, principal, bookmarked);
    }

    private ArticleResponse response(
        Article article,
        boolean content,
        boolean history,
        UserPrincipal principal,
        Boolean bookmarked
    ) {
        List<ArticleResponse.ReviewResponse> events = history
            ? reviews
                  .findByArticleIdOrderByIdDesc(article.getId())
                  .stream()
                  .map(review ->
                      new ArticleResponse.ReviewResponse(
                          review.getId(),
                          review.getActor().getUsername(),
                          review.getAction(),
                          review.getComment(),
                          review.getArticleVersion(),
                          review.getCreatedAt()
                      )
                  )
                  .toList()
            : null;
        return new ArticleResponse(
            article.getId(),
            article.getAuthor().getId(),
            article.getAuthor().getUsername(),
            article.getTitle(),
            article.getSummary(),
            content ? article.getContent() : null,
            article.getContentFormat(),
            article.getCategory(),
            article.getTags().stream().sorted().toList(),
            article.getStatus(),
            article.getVersion(),
            article.getViewCount(),
            article.getBookmarkCount(),
            article.getCreatedAt(),
            article.getUpdatedAt(),
            article.getPublishedAt(),
            bookmarked,
            events,
            article.isCommunity(),
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM article_comments WHERE article_id=? AND deleted=FALSE",
                Long.class,
                article.getId()
            ),
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM article_likes WHERE article_id=?",
                Long.class,
                article.getId()
            )
        );
    }
}
