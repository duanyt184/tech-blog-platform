package com.techblog.platform.service;

import com.techblog.platform.exception.*;
import com.techblog.platform.security.UserPrincipal;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ForumService {

    private final JdbcTemplate jdbc;

    public ForumService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RuleWrite(
        @NotBlank @Size(max = 100) String title,
        @NotNull @Size(max = 2000) String description,
        @NotNull @Size(max = 200) String pattern,
        boolean enabled
    ) {}

    public record TagWrite(
        @NotBlank @Pattern(regexp = "[\\p{L}\\p{N}_-]{1,24}") String name,
        @NotNull @Size(max = 200) String description,
        @Size(max = 80) String groupName
    ) {}

    public record ReportWrite(@NotBlank @Size(max = 500) String reason) {}

    public record ResolveWrite(
        @Pattern(regexp = "DISMISS|DELETE") @NotNull String action,
        @NotNull @Size(max = 500) String note
    ) {}

    public List<Map<String, Object>> rules(boolean admin) {
        return jdbc.queryForList(
            admin
                ? "SELECT * FROM community_rules ORDER BY id"
                : "SELECT id,title,description FROM community_rules WHERE enabled=TRUE ORDER BY id"
        );
    }

    @Transactional
    public void saveRule(Long id, RuleWrite r) {
        if (id == null) jdbc.update(
            "INSERT INTO community_rules(title,description,pattern,enabled) VALUES(?,?,?,?)",
            r.title().trim(),
            r.description(),
            r.pattern(),
            r.enabled()
        );
        else if (
            jdbc.update(
                "UPDATE community_rules SET title=?,description=?,pattern=?,enabled=? WHERE id=?",
                r.title().trim(),
                r.description(),
                r.pattern(),
                r.enabled(),
                id
            ) == 0
        ) throw new NotFoundException("规则不存在");
    }

    @Transactional
    public void deleteRule(Long id) {
        jdbc.update("DELETE FROM community_rules WHERE id=?", id);
    }

    public List<Map<String, Object>> tags() {
        return jdbc.queryForList(
            "SELECT t.name,t.description,t.group_name AS groupName,COUNT(a.id) AS count FROM forum_tags t LEFT JOIN article_tags atg ON atg.tag=t.name LEFT JOIN articles a ON a.id=atg.article_id AND a.deleted=FALSE AND a.status='PUBLISHED' GROUP BY t.name,t.description,t.group_name ORDER BY count DESC,t.name"
        );
    }

    @Transactional
    public void saveTag(TagWrite r) {
        jdbc.update(
            "INSERT INTO forum_tags(name,description,group_name) VALUES(?,?,?) ON DUPLICATE KEY UPDATE description=VALUES(description),group_name=VALUES(group_name)",
            r.name().toLowerCase(Locale.ROOT),
            r.description(),
            r.groupName() == null || r.groupName().isBlank()
                ? "Other Tags"
                : r.groupName().trim()
        );
    }

    @Transactional
    public void deleteTag(String name) {
        jdbc.update("DELETE FROM article_tags WHERE tag=?", name);
        jdbc.update("DELETE FROM forum_tags WHERE name=?", name);
    }

    @Transactional
    public void detect(Long articleId, Long commentId, String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        var matches = new ArrayList<String>();
        for (var rule : jdbc.queryForList(
            "SELECT title,pattern FROM community_rules WHERE enabled=TRUE AND pattern<>''"
        )) {
            if (
                Arrays.stream(rule.get("pattern").toString().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .anyMatch(s -> lower.contains(s.toLowerCase(Locale.ROOT)))
            ) matches.add(rule.get("title").toString());
        }
        if (content.split("https?://", -1).length > 6) matches.add(
            "Unusual number of links"
        );
        if (matches.isEmpty()) return;
        jdbc.queryForList(
            "SELECT id FROM articles WHERE id=? FOR UPDATE",
            articleId
        );
        if (
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM moderation_queue WHERE article_id=? AND comment_id <=> ? AND source='AUTO' AND status='PENDING'",
                Long.class,
                articleId,
                commentId
            ) > 0
        ) return;
        String reason = String.join("; ", matches);
        jdbc.update(
            "INSERT INTO moderation_queue(article_id,comment_id,source,reason) VALUES(?,?,'AUTO',?)",
            articleId,
            commentId,
            reason.substring(0, Math.min(500, reason.length()))
        );
    }

    @Transactional
    public void report(
        Long articleId,
        Long commentId,
        String reason,
        UserPrincipal p
    ) {
        var found = jdbc.queryForList(
            "SELECT id FROM articles WHERE id=? AND deleted=FALSE AND status='PUBLISHED' FOR UPDATE",
            articleId
        );
        if (found.isEmpty()) throw new NotFoundException("话题不存在");
        if (
            commentId != null &&
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM article_comments WHERE id=? AND article_id=? AND deleted=FALSE",
                Long.class,
                commentId,
                articleId
            ) == 0
        ) throw new NotFoundException("回复不存在");
        if (reason.isBlank()) throw new BadRequestException("请填写举报原因");
        if (
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM moderation_queue WHERE article_id=? AND comment_id <=> ? AND reporter_id=? AND status='PENDING'",
                Long.class,
                articleId,
                commentId,
                p.getId()
            ) > 0
        ) throw new ConflictException("举报已经提交");
        jdbc.update(
            "INSERT INTO moderation_queue(article_id,comment_id,reporter_id,source,reason) VALUES(?,?,?,'USER',?)",
            articleId,
            commentId,
            p.getId(),
            reason
        );
    }

    public List<Map<String, Object>> queue(String status, int page) {
        if (
            !List.of("PENDING", "DISMISSED", "DELETED").contains(status) ||
            page < 0 ||
            page > 10000
        ) throw new BadRequestException("筛选无效");
        return jdbc.queryForList(
            "SELECT q.*,a.title,COALESCE(c.content,a.content) AS content,u.username AS reporter FROM moderation_queue q JOIN articles a ON a.id=q.article_id LEFT JOIN article_comments c ON c.id=q.comment_id LEFT JOIN users u ON u.id=q.reporter_id WHERE q.status=? ORDER BY q.id DESC LIMIT 50 OFFSET ?",
            status,
            page * 50
        );
    }

    @Transactional
    public void resolve(Long id, ResolveWrite r, UserPrincipal p) {
        var rows = jdbc.queryForList(
            "SELECT * FROM moderation_queue WHERE id=? FOR UPDATE",
            id
        );
        if (rows.isEmpty()) throw new NotFoundException("举报不存在");
        var q = rows.get(0);
        if (!q.get("status").equals("PENDING")) throw new ConflictException(
            "该举报已处理"
        );
        if (r.action().equals("DELETE")) {
            if (q.get("comment_id") != null) {
                var pending = new ArrayDeque<Long>();
                pending.add(((Number) q.get("comment_id")).longValue());
                var seen = new HashSet<Long>();
                while (!pending.isEmpty()) {
                    Long comment = pending.remove();
                    if (!seen.add(comment)) continue;
                    pending.addAll(
                        jdbc.queryForList(
                            "SELECT id FROM article_comments WHERE parent_id=?",
                            Long.class,
                            comment
                        )
                    );
                    jdbc.update(
                        "UPDATE article_comments SET deleted=TRUE WHERE id=?",
                        comment
                    );
                    jdbc.update(
                        "UPDATE moderation_queue SET status='DELETED',resolution=?,handler_id=?,resolved_at=NOW(6) WHERE comment_id=? AND status='PENDING' AND id<>?",
                        r.note(),
                        p.getId(),
                        comment,
                        id
                    );
                }
            } else jdbc.update(
                "UPDATE articles SET deleted=TRUE,version=version+1 WHERE id=?",
                q.get("article_id")
            );
        }
        String state = r.action().equals("DELETE") ? "DELETED" : "DISMISSED";
        jdbc.update(
            "UPDATE moderation_queue SET status=?,resolution=?,handler_id=?,resolved_at=NOW(6) WHERE id=?",
            state,
            r.note(),
            p.getId(),
            id
        );
        if (r.action().equals("DELETE")) jdbc.update(
            "UPDATE moderation_queue SET status='DELETED',resolution=?,handler_id=?,resolved_at=NOW(6) WHERE status='PENDING' AND article_id=? AND (? IS NULL OR comment_id=?)",
            r.note(),
            p.getId(),
            q.get("article_id"),
            q.get("comment_id"),
            q.get("comment_id")
        );
    }

    public List<Map<String, Object>> activity(Long userId, int page) {
        if (page < 0 || page > 10000) throw new BadRequestException("分页无效");
        return jdbc.queryForList(
            "SELECT * FROM (SELECT 'TOPIC' AS type,a.id AS topicId,a.title,a.published_at AS createdAt,NULL AS commentId FROM articles a WHERE a.author_id=? AND a.status='PUBLISHED' AND a.deleted=FALSE UNION ALL SELECT 'REPLY',a.id,a.title,c.created_at,c.id FROM article_comments c JOIN articles a ON a.id=c.article_id WHERE c.user_id=? AND c.deleted=FALSE AND a.deleted=FALSE AND a.status='PUBLISHED') activity ORDER BY createdAt DESC LIMIT 30 OFFSET ?",
            userId,
            userId,
            page * 30
        );
    }

    public Map<String, Object> summary(Long id) {
        var stats = jdbc.queryForMap(
            "SELECT (SELECT COUNT(*) FROM articles WHERE author_id=u.id AND status='PUBLISHED' AND deleted=FALSE) AS topics,(SELECT COUNT(*) FROM article_comments c JOIN articles a ON a.id=c.article_id WHERE c.user_id=u.id AND c.deleted=FALSE AND a.deleted=FALSE AND a.status='PUBLISHED') AS replies,(SELECT COUNT(*) FROM article_likes l JOIN articles a ON a.id=l.article_id WHERE l.user_id=u.id AND a.deleted=FALSE AND a.status='PUBLISHED') AS likesGiven,(SELECT COUNT(*) FROM article_likes l JOIN articles a ON a.id=l.article_id WHERE a.author_id=u.id AND a.deleted=FALSE AND a.status='PUBLISHED') AS likesReceived,(SELECT COALESCE(SUM(view_count),0) FROM articles WHERE author_id=u.id AND status='PUBLISHED' AND deleted=FALSE) AS topicViews FROM users u WHERE u.id=?",
            id
        );
        var topics = jdbc.queryForList(
            "SELECT a.id,a.title,a.published_at AS createdAt,COUNT(l.id) AS likes FROM articles a LEFT JOIN article_likes l ON l.article_id=a.id WHERE a.author_id=? AND a.status='PUBLISHED' AND a.deleted=FALSE GROUP BY a.id,a.title,a.published_at ORDER BY likes DESC,a.published_at DESC LIMIT 5",
            id
        );
        var replies = jdbc.queryForList(
            "SELECT c.id,c.article_id AS topicId,a.title,c.content,c.created_at AS createdAt,COUNT(l.user_id) AS likes FROM article_comments c JOIN articles a ON a.id=c.article_id LEFT JOIN comment_likes l ON l.comment_id=c.id WHERE c.user_id=? AND c.deleted=FALSE AND a.deleted=FALSE AND a.status='PUBLISHED' GROUP BY c.id,c.article_id,a.title,c.content,c.created_at ORDER BY likes DESC,c.created_at DESC LIMIT 5",
            id
        );
        return Map.of("stats", stats, "topics", topics, "replies", replies);
    }

    public List<Map<String, Object>> users(String q, int page) {
        if (
            q.length() > 100 || page < 0 || page > 10000
        ) throw new BadRequestException("搜索条件无效");
        String pattern =
            "%" +
            q.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") +
            "%";
        return jdbc.queryForList(
            "SELECT id,username,display_name AS displayName,bio FROM users WHERE enabled=TRUE AND (username LIKE ? ESCAPE '!' OR display_name LIKE ? ESCAPE '!') ORDER BY username LIMIT 30 OFFSET ?",
            pattern,
            pattern,
            page * 30
        );
    }

    public Map<String, Object> topicStats(Long id) {
        if (
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM articles WHERE id=? AND status='PUBLISHED' AND deleted=FALSE",
                Long.class,
                id
            ) == 0
        ) throw new NotFoundException("话题不存在");
        return jdbc.queryForMap(
            "SELECT (SELECT COUNT(*) FROM article_comments WHERE article_id=a.id AND deleted=FALSE) AS replies,(SELECT COUNT(*) FROM article_likes WHERE article_id=a.id) AS likes,GREATEST(a.updated_at,COALESCE((SELECT MAX(created_at) FROM article_comments WHERE article_id=a.id AND deleted=FALSE),a.updated_at)) AS activity FROM articles a WHERE a.id=?",
            id
        );
    }

    public Map<String, Object> commentLikes(Long id, UserPrincipal p) {
        visibleComment(id);
        return Map.of(
            "count",
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM comment_likes WHERE comment_id=?",
                Long.class,
                id
            ),
            "liked",
            p != null &&
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM comment_likes WHERE comment_id=? AND user_id=?",
                    Long.class,
                    id,
                    p.getId()
                ) > 0
        );
    }

    private void visibleComment(Long id) {
        if (
            jdbc
                .queryForList(
                    "SELECT c.id FROM article_comments c JOIN articles a ON a.id=c.article_id WHERE c.id=? AND c.deleted=FALSE AND a.deleted=FALSE AND a.status='PUBLISHED'",
                    id
                )
                .isEmpty()
        ) throw new NotFoundException("回复不存在");
    }

    @Transactional
    public Map<String, Object> likeComment(
        Long id,
        boolean liked,
        UserPrincipal p
    ) {
        visibleComment(id);
        if (liked) jdbc.update(
            "INSERT INTO comment_likes(comment_id,user_id) VALUES(?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",
            id,
            p.getId()
        );
        else jdbc.update(
            "DELETE FROM comment_likes WHERE comment_id=? AND user_id=?",
            id,
            p.getId()
        );
        return commentLikes(id, p);
    }
}
