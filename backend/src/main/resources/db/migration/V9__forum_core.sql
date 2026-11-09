-- Preserve historical drafts and content; retire the submission gate.
UPDATE articles SET status='DRAFT', community=FALSE WHERE status IN ('PENDING','REJECTED');
UPDATE articles SET community=TRUE WHERE status='PUBLISHED';
ALTER TABLE categories ADD COLUMN parent_id BIGINT NULL,
 ADD CONSTRAINT fk_category_parent FOREIGN KEY(parent_id) REFERENCES categories(id);
CREATE TABLE forum_tags (
 name VARCHAR(24) PRIMARY KEY, description VARCHAR(200) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO forum_tags(name) SELECT DISTINCT tag FROM article_tags;
CREATE TABLE community_rules (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(100) NOT NULL,
 description VARCHAR(2000) NOT NULL DEFAULT '', pattern VARCHAR(200) NOT NULL DEFAULT '',
 enabled BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO community_rules(title,description,pattern) VALUES
 ('Be respectful','Discuss ideas constructively. Harassment, threats and personal attacks are not welcome.',''),
 ('Keep it useful','Stay on topic, search before posting, and share enough detail for others to help.',''),
 ('No spam or scams','Unsolicited promotions and scams are reviewed by moderators.','guaranteed profit,free crypto,刷单返利,加群博彩'),
 ('Protect personal information','Do not share passwords, credentials or private information.','-----BEGIN PRIVATE KEY-----');
CREATE TABLE moderation_queue (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 article_id BIGINT NOT NULL, comment_id BIGINT NULL, reporter_id BIGINT NULL,
 source VARCHAR(20) NOT NULL, reason VARCHAR(500) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', resolution VARCHAR(500) NOT NULL DEFAULT '',
 handler_id BIGINT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), resolved_at DATETIME(6) NULL,
 FOREIGN KEY(article_id) REFERENCES articles(id), FOREIGN KEY(comment_id) REFERENCES article_comments(id),
 FOREIGN KEY(reporter_id) REFERENCES users(id), FOREIGN KEY(handler_id) REFERENCES users(id),
 INDEX idx_moderation_status(status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE comment_likes (
 comment_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 PRIMARY KEY(comment_id,user_id), FOREIGN KEY(comment_id) REFERENCES article_comments(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO categories(name,description,color) VALUES
 ('Announcements','News and updates from the community.','#e9a441'),
 ('Discussions','Talk shop. Share workflows, tips, and ideas with other builders.','#6d9e6f'),
 ('Support','Ask questions and get help from the community.','#6893c5'),
 ('Ideas','Feature requests and feedback on how things could work better.','#bb88b9'),
 ('Guides','Tutorials, walkthroughs, and things you have learned.','#56a89c'),
 ('Showcase','Share what you have built.','#e28b64'),
 ('Meta','Discuss the forum and help make it better.','#92918c');
INSERT IGNORE INTO categories(name,description,color,parent_id)
 SELECT 'Bug Reports','Report a reproducible issue and help others find a solution.','#d96b61',id FROM categories WHERE name='Support';
INSERT IGNORE INTO categories(name,description,color,parent_id)
 SELECT 'Help','Questions about setup, configuration, and troubleshooting.','#6893c5',id FROM categories WHERE name='Support';
