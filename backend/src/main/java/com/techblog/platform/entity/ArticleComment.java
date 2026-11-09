package com.techblog.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "article_comments")
public class ArticleComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    /** 被回复的评论；顶层评论为 null。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ArticleComment parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 软删除：删除父评论时整棵子树一起标记，避免出现无主的回复。 */
    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    protected ArticleComment() {
    }

    public ArticleComment(Article article, User user, String content, ArticleComment parent) {
        this.article = article;
        this.user = user;
        this.content = content;
        this.parent = parent;
    }

    public Long getId() { return id; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public Article getArticle() { return article; }
    public ArticleComment getParent() { return parent; }
    public User getUser() { return user; }
    public void setContent(String content) { this.content=content; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
