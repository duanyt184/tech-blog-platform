-- Keep existing moderation work visible after switching queues. Preserve resolved history too.
INSERT INTO moderation_queue(article_id,comment_id,reporter_id,source,reason,status,handler_id,created_at,resolved_at)
SELECT c.article_id,r.comment_id,r.reporter_id,'USER',r.reason,
 CASE WHEN (c.deleted=TRUE OR a.deleted=TRUE) AND r.status='PENDING' THEN 'DELETED' ELSE r.status END,
 r.handled_by,r.created_at,r.handled_at
FROM comment_reports r JOIN article_comments c ON c.id=r.comment_id JOIN articles a ON a.id=c.article_id;
