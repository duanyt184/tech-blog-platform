-- 收藏数冗余列：公开列表要展示收藏数，并支持按收藏数排序。
-- 用计数列而不是每次列表做聚合子查询，翻页和排序都能走索引。
ALTER TABLE articles ADD COLUMN bookmark_count BIGINT NOT NULL DEFAULT 0 AFTER view_count;

-- 已有收藏关系一次性回填，保证历史数据与计数一致。
UPDATE articles a
SET a.bookmark_count = (SELECT COUNT(*) FROM article_bookmarks b WHERE b.article_id = a.id);

ALTER TABLE articles ADD INDEX idx_articles_public_bookmarks (deleted, status, bookmark_count);
