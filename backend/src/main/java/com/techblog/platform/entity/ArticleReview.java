package com.techblog.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "article_reviews")
public class ArticleReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id")
    private Article article;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;
    @Column(nullable = false, length = 20)
    private String action;
    @Column(nullable = false, length = 2000)
    private String comment;
    @Column(name = "article_version", nullable = false)
    private Long articleVersion;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ArticleReview() { }
    public ArticleReview(Article article, User actor, String action, String comment) {
        this.article = article;
        this.actor = actor;
        this.action = action;
        this.comment = comment;
        this.articleVersion = article.getVersion();
        this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getComment() { return comment; }
    public Long getArticleVersion() { return articleVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
