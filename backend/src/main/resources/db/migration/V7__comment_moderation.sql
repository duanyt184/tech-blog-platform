-- 评论软删除与举报：作者/管理员删除评论，作者与编辑处理举报。
ALTER TABLE article_comments
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 AFTER parent_id;

CREATE TABLE comment_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    handled_by BIGINT NULL,
    handled_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_reports_status (status, created_at),
    KEY idx_reports_comment (comment_id),
    CONSTRAINT fk_reports_comment FOREIGN KEY (comment_id) REFERENCES article_comments (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_handler FOREIGN KEY (handled_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
