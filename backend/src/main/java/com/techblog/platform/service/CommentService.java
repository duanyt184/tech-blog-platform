package com.techblog.platform.service;

import com.techblog.platform.dto.CommentReportResponse;
import com.techblog.platform.dto.CommentRequest;
import com.techblog.platform.dto.CommentResponse;
import com.techblog.platform.dto.MyCommentsResponse;
import com.techblog.platform.entity.Article;
import com.techblog.platform.entity.ArticleComment;
import com.techblog.platform.entity.ArticleStatus;
import com.techblog.platform.entity.CommentReport;
import com.techblog.platform.entity.ReportStatus;
import com.techblog.platform.entity.UserRole;
import com.techblog.platform.exception.BadRequestException;
import com.techblog.platform.exception.ConflictException;
import com.techblog.platform.exception.NotFoundException;
import com.techblog.platform.repository.ArticleCommentRepository;
import com.techblog.platform.repository.ArticleRepository;
import com.techblog.platform.repository.CommentReportRepository;
import com.techblog.platform.repository.UserRepository;
import com.techblog.platform.security.UserPrincipal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommentService {

    private static final int DETAIL_LIMIT = 200;
    private static final int MY_LIST_LIMIT = 100;
    private static final int REPORT_LIMIT = 200;

    private final ForumService forum;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ArticleCommentRepository comments;
    private final ArticleRepository articles;
    private final UserRepository users;
    private final CommentReportRepository reports;

    public CommentService(
        ArticleCommentRepository comments,
        ArticleRepository articles,
        UserRepository users,
        CommentReportRepository reports,
        ForumService forum,
        org.springframework.jdbc.core.JdbcTemplate jdbc
    ) {
        this.forum = forum;
        this.jdbc = jdbc;
        this.comments = comments;
        this.articles = articles;
        this.users = users;
        this.reports = reports;
    }

    public List<CommentResponse> list(Long articleId) {
        return list(articleId, 0, 50);
    }

    public List<CommentResponse> list(Long articleId, int page, int size) {
        if (
            page < 0 || page > 10000 || size < 1 || size > 100
        ) throw new BadRequestException("分页无效");
        Article article = publishedArticle(articleId);
        return comments
            .findForArticle(article.getId(), PageRequest.of(page, size))
            .stream()
            .map(comment -> response(comment, false))
            .toList();
    }

    @Transactional
    public CommentResponse add(
        Long articleId,
        CommentRequest request,
        UserPrincipal principal
    ) {
        requireUser(principal);
        String content =
            request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new BadRequestException("评论内容不能为空");
        }
        Article article = publishedArticle(articleId);
        ArticleComment parent = null;
        if (request.parentId() != null) {
            parent = comments
                .findById(request.parentId())
                .filter(c -> !c.isDeleted())
                .orElseThrow(() ->
                    new BadRequestException("要回复的评论不存在")
                );
            if (!parent.getArticle().getId().equals(article.getId())) {
                throw new BadRequestException("要回复的评论不属于这篇文章");
            }
        }
        ArticleComment saved = comments.saveAndFlush(
            new ArticleComment(
                article,
                users.getReferenceById(principal.getId()),
                content,
                parent
            )
        );
        forum.detect(articleId, saved.getId(), content);
        jdbc.update(
            "UPDATE articles SET updated_at=NOW(6) WHERE id=?",
            articleId
        );
        return new CommentResponse(
            saved.getId(),
            article.getId(),
            principal.getId(),
            null,
            principal.getUsername(),
            saved.getContent(),
            saved.getCreatedAt(),
            parent == null ? null : parent.getId()
        );
    }

    @Transactional
    public CommentResponse edit(
        Long id,
        CommentRequest request,
        UserPrincipal principal
    ) {
        requireUser(principal);
        var c = comments
            .findById(id)
            .filter(v -> !v.isDeleted())
            .orElseThrow(() -> new NotFoundException("回复不存在"));
        publishedArticle(c.getArticle().getId());
        if (
            !c.getUser().getId().equals(principal.getId())
        ) throw new AccessDeniedException("只能编辑自己的回复");
        if (request.content().isBlank()) throw new BadRequestException(
            "回复不能为空"
        );
        c.setContent(request.content().trim());
        comments.flush();
        forum.detect(c.getArticle().getId(), c.getId(), c.getContent());
        return response(c, false);
    }

    public MyCommentsResponse mine(UserPrincipal principal) {
        requireUser(principal);
        PageRequest limit = PageRequest.of(0, MY_LIST_LIMIT);
        List<CommentResponse> written = comments
            .findWritten(principal.getId(), limit)
            .stream()
            .map(comment -> response(comment, true))
            .toList();
        List<CommentResponse> received = comments
            .findReceived(principal.getId(), limit)
            .stream()
            .map(comment -> response(comment, true))
            .toList();
        return new MyCommentsResponse(written, received);
    }

    /** 作者删除自己的评论；编辑与管理员可以删除任意评论。删除父评论会连带删除整棵子树。 */
    @Transactional
    public void delete(Long commentId, UserPrincipal principal) {
        requireUser(principal);
        ArticleComment comment = comments
            .findById(commentId)
            .filter(item -> !item.isDeleted())
            .orElseThrow(() -> new NotFoundException("评论不存在"));
        boolean moderator =
            principal.getRole() == UserRole.EDITOR ||
            principal.getRole() == UserRole.ADMIN;
        if (
            !moderator && !comment.getUser().getId().equals(principal.getId())
        ) {
            throw new AccessDeniedException("只能删除自己的评论");
        }
        softDeleteTree(comment);
        // 评论已被作者删除时，它的待处理举报一并归档，避免举报队列里留下已消失的内容
        var handler = users.getReferenceById(principal.getId());
        for (CommentReport report : reports.findByCommentIdAndStatus(
            commentId,
            ReportStatus.PENDING
        )) {
            report.resolve(ReportStatus.DELETED, handler);
        }
    }

    @Transactional
    public void report(Long commentId, String reason, UserPrincipal principal) {
        requireUser(principal);
        var c = comments
            .findById(commentId)
            .orElseThrow(() -> new NotFoundException("回复不存在"));
        forum.report(c.getArticle().getId(), commentId, reason, principal);
    }

    public List<CommentReportResponse> pendingReports(UserPrincipal principal) {
        requireEditor(principal);
        return reports
            .findByStatus(ReportStatus.PENDING, PageRequest.of(0, REPORT_LIMIT))
            .stream()
            .map(this::reportResponse)
            .toList();
    }

    @Transactional
    public void resolveReport(
        Long reportId,
        String action,
        UserPrincipal principal
    ) {
        requireEditor(principal);
        CommentReport report = reports
            .findById(reportId)
            .orElseThrow(() -> new NotFoundException("举报不存在"));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new ConflictException("该举报已处理");
        }
        var handler = users.getReferenceById(principal.getId());
        if ("DELETE".equalsIgnoreCase(action)) {
            softDeleteTree(report.getComment());
            for (CommentReport other : reports.findByCommentIdAndStatus(
                report.getComment().getId(),
                ReportStatus.PENDING
            )) {
                other.resolve(ReportStatus.DELETED, handler);
            }
        } else if ("DISMISS".equalsIgnoreCase(action)) {
            report.resolve(ReportStatus.DISMISSED, handler);
        } else {
            throw new BadRequestException("处理方式不合法");
        }
    }

    private void softDeleteTree(ArticleComment root) {
        List<ArticleComment> all = comments.findAllByArticleId(
            root.getArticle().getId()
        );
        Map<Long, List<ArticleComment>> children = new HashMap<>();
        for (ArticleComment item : all) {
            if (item.getParent() != null) {
                children
                    .computeIfAbsent(item.getParent().getId(), key ->
                        new ArrayList<>()
                    )
                    .add(item);
            }
        }
        Deque<ArticleComment> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ArticleComment node = queue.poll();
            if (!seen.add(node.getId())) {
                continue;
            }
            node.setDeleted(true);
            queue.addAll(children.getOrDefault(node.getId(), List.of()));
        }
    }

    private CommentReportResponse reportResponse(CommentReport report) {
        ArticleComment comment = report.getComment();
        return new CommentReportResponse(
            report.getId(),
            comment.getId(),
            comment.getArticle().getId(),
            comment.getArticle().getTitle(),
            comment.getUser().getUsername(),
            comment.getContent(),
            report.getReporter().getUsername(),
            report.getReason(),
            report.getStatus(),
            report.getCreatedAt()
        );
    }

    private Article publishedArticle(Long articleId) {
        Article article = articles
            .findByIdAndDeletedFalse(articleId)
            .orElseThrow(() -> new NotFoundException("文章不存在"));
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new NotFoundException("文章不存在或尚未发布");
        }
        return article;
    }

    private CommentResponse response(
        ArticleComment comment,
        boolean withArticle
    ) {
        return new CommentResponse(
            comment.getId(),
            comment.getArticle().getId(),
            comment.getUser().getId(),
            withArticle ? comment.getArticle().getTitle() : null,
            comment.getUser().getUsername(),
            comment.getContent(),
            comment.getCreatedAt(),
            comment.getParent() == null ? null : comment.getParent().getId()
        );
    }

    private void requireUser(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("请先登录");
        }
    }

    private void requireEditor(UserPrincipal principal) {
        requireUser(principal);
        if (
            principal.getRole() != UserRole.EDITOR &&
            principal.getRole() != UserRole.ADMIN
        ) {
            throw new AccessDeniedException("没有处理举报的权限");
        }
    }
}
