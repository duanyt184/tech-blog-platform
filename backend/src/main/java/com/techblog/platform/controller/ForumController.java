package com.techblog.platform.controller;

import com.techblog.platform.security.UserPrincipal;
import com.techblog.platform.service.ForumService;
import com.techblog.platform.service.ForumService.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ForumController {

    private final ForumService forum;

    public ForumController(ForumService forum) {
        this.forum = forum;
    }

    @GetMapping("/community/rules")
    public Object rules() {
        return forum.rules(false);
    }

    @GetMapping("/community/tags")
    public Object tags() {
        return forum.tags();
    }

    @GetMapping("/profiles/{id}/activity")
    public Object activity(
        @PathVariable Long id,
        @RequestParam(defaultValue = "0") int page
    ) {
        return forum.activity(id, page);
    }

    @GetMapping("/profiles/{id}/summary")
    public Object summary(@PathVariable Long id) {
        return forum.summary(id);
    }

    @GetMapping("/community/users")
    public Object users(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "0") int page
    ) {
        return forum.users(q, page);
    }

    @GetMapping("/articles/{id}/stats")
    public Object stats(@PathVariable Long id) {
        return forum.topicStats(id);
    }

    @PostMapping("/articles/{id}/reports")
    public void report(
        @PathVariable Long id,
        @Valid @RequestBody ReportWrite r,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        forum.report(id, null, r.reason(), p);
    }

    @GetMapping("/editor/moderation")
    public Object queue(
        @RequestParam(defaultValue = "PENDING") String status,
        @RequestParam(defaultValue = "0") int page
    ) {
        return forum.queue(status, page);
    }

    @PostMapping("/editor/moderation/{id}/resolve")
    public void resolve(
        @PathVariable Long id,
        @Valid @RequestBody ResolveWrite r,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        forum.resolve(id, r, p);
    }

    @GetMapping("/admin/rules")
    public Object adminRules() {
        return forum.rules(true);
    }

    @PostMapping("/admin/rules")
    public void addRule(@Valid @RequestBody RuleWrite r) {
        forum.saveRule(null, r);
    }

    @PutMapping("/admin/rules/{id}")
    public void editRule(
        @PathVariable Long id,
        @Valid @RequestBody RuleWrite r
    ) {
        forum.saveRule(id, r);
    }

    @DeleteMapping("/admin/rules/{id}")
    public void deleteRule(@PathVariable Long id) {
        forum.deleteRule(id);
    }

    @PostMapping("/admin/tags")
    public void saveTag(@Valid @RequestBody TagWrite r) {
        forum.saveTag(r);
    }

    @DeleteMapping("/admin/tags/{name}")
    public void deleteTag(@PathVariable String name) {
        forum.deleteTag(name);
    }

    @GetMapping("/comments/{id}/likes")
    public Object commentLikes(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        return forum.commentLikes(id, p);
    }

    @PutMapping("/comments/{id}/likes")
    public Object likeComment(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        return forum.likeComment(id, true, p);
    }

    @DeleteMapping("/comments/{id}/likes")
    public Object unlikeComment(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal p
    ) {
        return forum.likeComment(id, false, p);
    }
}
