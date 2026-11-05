-- 评论回复：parent_id 指向被回复的评论，支持多层对话。
ALTER TABLE article_comments
    ADD COLUMN parent_id BIGINT NULL AFTER article_id,
    ADD KEY idx_comments_parent (parent_id),
    ADD CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES article_comments (id) ON DELETE CASCADE;
