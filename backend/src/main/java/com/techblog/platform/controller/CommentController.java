package com.techblog.platform.controller;

import com.techblog.platform.dto.CommentReportRequest;
import com.techblog.platform.dto.CommentReportResponse;
import com.techblog.platform.dto.CommentRequest;
import com.techblog.platform.dto.CommentResponse;
import com.techblog.platform.dto.MyCommentsResponse;
import com.techblog.platform.dto.ReportResolveRequest;
import com.techblog.platform.security.UserPrincipal;
import com.techblog.platform.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 评论接口。
 *
 * <pre>
 * GET    /api/articles/{id}/comments   200 [...]                公开文章的评论（无需登录）
 * POST   /api/articles/{id}/comments   201 {...}                发表评论（需登录）
 * GET    /api/my/comments              200 {written, received}  我的评论 + 我收到的评论（需登录）
 * DELETE /api/comments/{id}            204                      删除评论（作者本人或编辑/管理员）
 * POST   /api/comments/{id}/reports    201                      举报评论（需登录，不能举报自己）
 * GET    /api/editor/reports           200 [...]                待处理举报（编辑/管理员）
 * POST   /api/editor/reports/{id}/resolve 204                   DELETE 删除评论 / DISMISS 忽略
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping("/articles/{id}/comments")
    public List<CommentResponse> list(@PathVariable Long id) {
        return service.list(id);
    }

    @PostMapping("/articles/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(@PathVariable Long id, @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.add(id, request, principal);
    }

    @GetMapping("/my/comments")
    public MyCommentsResponse mine(@AuthenticationPrincipal UserPrincipal principal) {
        return service.mine(principal);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(id, principal);
    }

    @PostMapping("/comments/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@PathVariable Long id, @Valid @RequestBody CommentReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.report(id, request.reason(), principal);
    }

    @GetMapping("/editor/reports")
    public List<CommentReportResponse> reports(@AuthenticationPrincipal UserPrincipal principal) {
        return service.pendingReports(principal);
    }

    @PostMapping("/editor/reports/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable Long id, @Valid @RequestBody ReportResolveRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.resolveReport(id, request.action(), principal);
    }
}
