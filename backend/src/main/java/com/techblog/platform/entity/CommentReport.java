package com.techblog.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 评论举报：同一用户对同一条评论只保留一条待处理记录。 */
@Entity
@Table(name = "comment_reports")
public class CommentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private ArticleComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    protected CommentReport() {
    }

    public CommentReport(ArticleComment comment, User reporter, String reason) {
        this.comment = comment;
        this.reporter = reporter;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void resolve(ReportStatus status, User handler) {
        this.status = status;
        this.handledBy = handler;
        this.handledAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public ArticleComment getComment() { return comment; }
    public User getReporter() { return reporter; }
    public String getReason() { return reason; }
    public ReportStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public User getHandledBy() { return handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
}
