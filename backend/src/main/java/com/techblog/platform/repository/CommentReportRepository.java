package com.techblog.platform.repository;

import com.techblog.platform.entity.CommentReport;
import com.techblog.platform.entity.ReportStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {

    boolean existsByCommentIdAndReporterIdAndStatus(Long commentId, Long reporterId, ReportStatus status);

    List<CommentReport> findByCommentIdAndStatus(Long commentId, ReportStatus status);

    @Query("select r from CommentReport r join fetch r.comment c join fetch c.user join fetch c.article a "
            + "join fetch r.reporter where r.status = :status order by r.createdAt desc, r.id desc")
    List<CommentReport> findByStatus(@Param("status") ReportStatus status, Pageable pageable);
}
