-- Existing articles were rendered with Markdown headings and code fences.
-- Keep that interpretation; new articles default to plain text in the application.
ALTER TABLE articles
    ADD COLUMN content_format VARCHAR(20) NOT NULL DEFAULT 'MARKDOWN';
