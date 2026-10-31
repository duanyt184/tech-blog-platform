ALTER TABLE articles
    ADD COLUMN category VARCHAR(40) NOT NULL DEFAULT '学习笔记',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD INDEX idx_articles_public_category (deleted, status, category, published_at),
    ADD INDEX idx_articles_owner_status (author_id, deleted, status, updated_at);

CREATE TABLE article_tags (
    article_id BIGINT NOT NULL,
    tag VARCHAR(24) NOT NULL,
    PRIMARY KEY (article_id, tag),
    CONSTRAINT fk_article_tags_article FOREIGN KEY (article_id) REFERENCES articles(id),
    INDEX idx_article_tags_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE article_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    comment VARCHAR(2000) NOT NULL DEFAULT '',
    article_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_reviews_article FOREIGN KEY (article_id) REFERENCES articles(id),
    CONSTRAINT fk_reviews_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT chk_review_action CHECK (action IN ('SUBMITTED', 'WITHDRAWN', 'PUBLISHED', 'REJECTED')),
    INDEX idx_reviews_article_time (article_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE article_bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_bookmarks_article FOREIGN KEY (article_id) REFERENCES articles(id),
    CONSTRAINT uk_bookmarks_user_article UNIQUE (user_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
