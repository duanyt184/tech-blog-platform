ALTER TABLE users ADD COLUMN display_name VARCHAR(80) NOT NULL DEFAULT '',
 ADD COLUMN bio VARCHAR(500) NOT NULL DEFAULT '', ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE articles ADD COLUMN community BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE categories (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(40) NOT NULL UNIQUE,
 description VARCHAR(300) NOT NULL DEFAULT '',
 color VARCHAR(7) NOT NULL DEFAULT '#3b82f6'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO categories (name, description, color) VALUES
 ('前端开发','构建更好的 Web 体验，分享框架、交互与性能实践。','#3b82f6'),
 ('后端开发','从 API 到系统架构，讨论可靠服务的实现。','#8b5cf6'),
 ('工程实践','工具、测试、部署与团队工作中的经验。','#f59e0b'),
 ('数据库','数据建模、查询优化与存储技术。','#10b981'),
 ('学习笔记','记录探索过程，把新知识变成自己的理解。','#06b6d4');
INSERT IGNORE INTO categories (name) SELECT DISTINCT category FROM articles;
CREATE TABLE article_likes (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, article_id BIGINT NOT NULL,
 CONSTRAINT fk_likes_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_likes_article FOREIGN KEY(article_id) REFERENCES articles(id),
 CONSTRAINT uk_likes_user_article UNIQUE(user_id,article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
