package com.techblog.platform.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techblog.platform.entity.User;
import com.techblog.platform.entity.UserRole;
import com.techblog.platform.repository.UserRepository;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

/** Real database + security chain tests. Run only against a disposable database. */
@SpringBootTest(properties = {
    "spring.datasource.url=${ARTICLE_TEST_DB_URL:jdbc:mariadb://127.0.0.1:3308/tech_blog_business_test}",
    "spring.datasource.username=${ARTICLE_TEST_DB_USER:business_test}",
    "spring.datasource.password=${ARTICLE_TEST_DB_PASSWORD:local-business-tests-only}"
})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "ARTICLE_DB_TEST", matches = "true")
class ArticleWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockHttpSession author;
    MockHttpSession other;
    MockHttpSession editor;

    @BeforeEach
    void actors() throws Exception {
        author = actor(UserRole.USER);
        other = actor(UserRole.USER);
        editor = actor(UserRole.EDITOR);
    }

    MockHttpSession actor(UserRole role) throws Exception {
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = User.create(name, name + "@example.test", passwords.encode("password123"));
        user.setRole(role);
        users.saveAndFlush(user);
        MockHttpSession session = new MockHttpSession();
        write(HttpMethod.POST, "/api/auth/login", session,
                Map.of("identifier", name, "password", "password123")).andExpect(status().isOk());
        return session;
    }

    ResultActions write(HttpMethod method, String path, MockHttpSession session, Object body) throws Exception {
        var token = tree(mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()));
        var request = request(method, path).session(session)
                .header(token.get("headerName").asText(), token.get("token").asText());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode tree(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    Map<String, Object> payload(long version, String content) {
        return Map.of("title", "事务与并发 100%_", "summary", "实践记录", "content", content,
                "category", "后端开发", "tags", List.of("Java", "transactions"), "version", version);
    }
    JsonNode draft(MockHttpSession session) throws Exception {
        return tree(write(HttpMethod.POST, "/api/my/articles", session, payload(0, "# 实践\n正文\n```java\nint count = 1;\n```"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")));
    }
    JsonNode submit(JsonNode draft, MockHttpSession session) throws Exception {
        return tree(write(HttpMethod.POST, "/api/my/articles/" + draft.get("id").asLong() + "/submit", session,
                Map.of("version", draft.get("version").asLong())).andExpect(status().isOk()));
    }
    JsonNode approve(JsonNode pending) throws Exception {
        return tree(write(HttpMethod.POST, "/api/editor/articles/" + pending.get("id").asLong() + "/review", editor,
                Map.of("version", pending.get("version").asLong(), "decision", "APPROVE", "comment", "内容清晰"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED")));
    }

    @Test void draftSubmissionReviewAndPublicReading() throws Exception {
        JsonNode draft = draft(author);
        long id = draft.get("id").asLong();
        mvc.perform(get("/api/articles/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/articles")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        JsonNode pending = submit(draft, author);
        assertTrue(pending.get("version").asLong() > draft.get("version").asLong());
        mvc.perform(get("/api/editor/articles").param("status", "PENDING").session(editor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        approve(pending);
        mvc.perform(get("/api/articles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.items[0].reviews").doesNotExist());
        mvc.perform(get("/api/articles/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists()).andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.reviews").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/my/articles/" + id).session(author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(2));
    }

    @Test void rejectionRequiresFeedbackAndAllowsResubmission() throws Exception {
        JsonNode pending = submit(draft(author), author);
        long id = pending.get("id").asLong();
        String path = "/api/editor/articles/" + id + "/review";
        write(HttpMethod.POST, path, editor, Map.of("version", pending.get("version").asLong(), "decision", "REJECT", "comment", "  "))
                .andExpect(status().isBadRequest());
        JsonNode rejected = tree(write(HttpMethod.POST, path, editor,
                Map.of("version", pending.get("version").asLong(), "decision", "REJECT", "comment", "请补充复现步骤"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviews[0].comment").value("请补充复现步骤")));
        JsonNode changed = tree(write(HttpMethod.PUT, "/api/my/articles/" + id, author,
                payload(rejected.get("version").asLong(), "已补充复现步骤")).andExpect(status().isOk()));
        approve(submit(changed, author));
        mvc.perform(get("/api/my/articles/" + id).session(author)).andExpect(jsonPath("$.reviews.length()").value(4));
    }

    @Test void ownershipAndEditorialRoleAreEnforced() throws Exception {
        JsonNode draft = draft(author);
        long id = draft.get("id").asLong();
        mvc.perform(get("/api/my/articles/" + id).session(other)).andExpect(status().isNotFound());
        write(HttpMethod.PUT, "/api/my/articles/" + id, other, payload(0, "越权修改")).andExpect(status().isNotFound());
        mvc.perform(get("/api/my/articles").session(other)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/my/articles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/editor/articles").session(author)).andExpect(status().isForbidden());
        mvc.perform(get("/api/editor/articles/" + id).session(editor)).andExpect(status().isNotFound());
        JsonNode pending = submit(draft, author);
        write(HttpMethod.POST, "/api/editor/articles/" + id + "/review", author,
                Map.of("version", pending.get("version").asLong(), "decision", "APPROVE"))
                .andExpect(status().isForbidden());
    }

    @Test void editorCannotApproveOwnArticle() throws Exception {
        JsonNode pending = submit(draft(editor), editor);
        write(HttpMethod.POST, "/api/editor/articles/" + pending.get("id").asLong() + "/review", editor,
                Map.of("version", pending.get("version").asLong(), "decision", "APPROVE"))
                .andExpect(status().isForbidden());
    }

    @Test void staleVersionsAndInvalidTransitionsAreRejected() throws Exception {
        JsonNode draft = draft(author);
        long id = draft.get("id").asLong();
        JsonNode changed = tree(write(HttpMethod.PUT, "/api/my/articles/" + id, author,
                payload(draft.get("version").asLong(), "新的正文")).andExpect(status().isOk()));
        write(HttpMethod.PUT, "/api/my/articles/" + id, author, payload(draft.get("version").asLong(), "旧页面修改"))
                .andExpect(status().isConflict());
        JsonNode pending = submit(changed, author);
        write(HttpMethod.PUT, "/api/my/articles/" + id, author, payload(pending.get("version").asLong(), "审核中修改"))
                .andExpect(status().isConflict());
        approve(pending);
        write(HttpMethod.POST, "/api/editor/articles/" + id + "/review", editor,
                Map.of("version", pending.get("version").asLong(), "decision", "REJECT", "comment", "重复处理"))
                .andExpect(status().isConflict());
    }

    @Test void withdrawingUnpublishesAndDraftDeletionIsSoft() throws Exception {
        JsonNode published = approve(submit(draft(author), author));
        long id = published.get("id").asLong();
        write(HttpMethod.DELETE, "/api/my/articles/" + id + "?version=" + published.get("version").asLong(), author, null)
                .andExpect(status().isConflict());
        JsonNode withdrawn = tree(write(HttpMethod.POST, "/api/my/articles/" + id + "/withdraw", author,
                Map.of("version", published.get("version").asLong())).andExpect(status().isOk()));
        mvc.perform(get("/api/articles/" + id)).andExpect(status().isNotFound());
        write(HttpMethod.DELETE, "/api/my/articles/" + id + "?version=" + withdrawn.get("version").asLong(), author, null)
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/my/articles/" + id).session(author)).andExpect(status().isNotFound());
        mvc.perform(get("/api/my/articles").session(author)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void accountBookmarksAreIdempotentAndPrivate() throws Exception {
        JsonNode published = approve(submit(draft(author), author));
        long id = published.get("id").asLong();
        write(HttpMethod.PUT, "/api/my/bookmarks/" + id, other, null).andExpect(status().isNoContent());
        write(HttpMethod.PUT, "/api/my/bookmarks/" + id, other, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/bookmarks").session(other)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/my/bookmarks").session(author)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/articles/" + id).session(other)).andExpect(jsonPath("$.bookmarked").value(true));
        write(HttpMethod.DELETE, "/api/my/bookmarks/" + id, other, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/bookmarks").session(other)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void filtersPaginationAndLiteralSearchAreBackedByDatabase() throws Exception {
        approve(submit(draft(author), author));
        mvc.perform(get("/api/articles").param("q", "100%_").param("category", "后端开发").param("tag", "java").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/articles").param("q", "transactions")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/articles").param("category", "前端开发")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/articles").param("size", "1").param("page", "1")).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/articles").param("size", "500")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles").param("page", "invalid")).andExpect(status().isBadRequest());
    }

    @Test void csrfValidationAndAuthContinueWorking() throws Exception {
        mvc.perform(post("/api/my/articles").session(author).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload(0, "正文")))).andExpect(status().isForbidden());
        MockHttpSession guest = new MockHttpSession();
        String name = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        write(HttpMethod.POST, "/api/auth/register", guest,
                Map.of("username", name, "email", name + "@example.test", "password", "password123", "confirmPassword", "password123"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
        write(HttpMethod.POST, "/api/auth/login", guest, Map.of("identifier", name, "password", "password123"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(guest)).andExpect(status().isOk());
        write(HttpMethod.POST, "/api/auth/logout", guest, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }


    @Test void readingDoesNotChangeEditorialVersionOrLoseViewsOnWithdrawal() throws Exception {
        JsonNode published = approve(submit(draft(author), author));
        long id = published.get("id").asLong();
        mvc.perform(get("/api/articles/" + id)).andExpect(status().isOk());
        write(HttpMethod.POST, "/api/my/articles/" + id + "/withdraw", author,
                Map.of("version", published.get("version").asLong())).andExpect(status().isOk());
        entityManager.clear();
        mvc.perform(get("/api/my/articles/" + id).session(author))
                .andExpect(status().isOk()).andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/articles").param("status", "DRAFT")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void emptyBodyCanBeSavedButNotSubmittedAndTagsAreValidated() throws Exception {
        JsonNode empty = tree(write(HttpMethod.POST, "/api/my/articles", author, payload(0, ""))
                .andExpect(status().isCreated()));
        write(HttpMethod.POST, "/api/my/articles/" + empty.get("id").asLong() + "/submit", author,
                Map.of("version", empty.get("version").asLong())).andExpect(status().isBadRequest());
        write(HttpMethod.POST, "/api/my/articles", author,
                Map.of("title", "标题", "content", "正文", "category", "不存在", "tags", List.of()))
                .andExpect(status().isBadRequest());
        write(HttpMethod.POST, "/api/my/articles", author,
                Map.of("title", "标题", "content", "正文", "category", "学习笔记", "tags", List.of(" ")))
                .andExpect(status().isBadRequest());
    }

    @Test void contentFormatsSurviveSaveReloadReviewAndPublication() throws Exception {
        String source = "  # 原文\n\n**加粗** $E=mc^2$\n\n$$\n\\frac{1}{2}\n$$\n";
        for (String format : List.of("PLAIN_TEXT", "MARKDOWN")) {
            var body = new HashMap<String, Object>(payload(0, source));
            body.put("contentFormat", format);
            JsonNode draft = tree(write(HttpMethod.POST, "/api/my/articles", author, body)
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.contentFormat").value(format)));
            long id = draft.get("id").asLong();
            entityManager.clear();
            mvc.perform(get("/api/my/articles/" + id).session(author))
                    .andExpect(jsonPath("$.contentFormat").value(format)).andExpect(jsonPath("$.content").value(source));
            // Requests from older clients must not reset a saved Markdown article to plain text.
            JsonNode saved = tree(write(HttpMethod.PUT, "/api/my/articles/" + id, author,
                    payload(draft.get("version").asLong(), source))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.contentFormat").value(format)));
            JsonNode pending = submit(saved, author);
            mvc.perform(get("/api/editor/articles/" + id).session(editor))
                    .andExpect(jsonPath("$.contentFormat").value(format)).andExpect(jsonPath("$.content").value(source));
            approve(pending);
            entityManager.clear();
            mvc.perform(get("/api/articles/" + id))
                    .andExpect(jsonPath("$.contentFormat").value(format)).andExpect(jsonPath("$.content").value(source));
            mvc.perform(get("/api/articles").param("q", "事务与并发"))
                    .andExpect(jsonPath("$.items[0].contentFormat").exists());
        }
    }

    @Test void formatDefaultsCanBeChangedAndInvalidFormatsAreRejected() throws Exception {
        JsonNode draft = draft(author);
        assertEquals("PLAIN_TEXT", draft.get("contentFormat").asText());
        long id = draft.get("id").asLong();
        String source = draft.get("content").asText();
        var body = new HashMap<String, Object>(payload(draft.get("version").asLong(), source));
        body.put("contentFormat", "MARKDOWN");
        JsonNode updated = tree(write(HttpMethod.PUT, "/api/my/articles/" + id, author, body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentFormat").value("MARKDOWN"))
                .andExpect(jsonPath("$.content").value(source)));
        body.put("version", updated.get("version").asLong());
        body.put("contentFormat", "PLAIN_TEXT");
        write(HttpMethod.PUT, "/api/my/articles/" + id, author, body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentFormat").value("PLAIN_TEXT"))
                .andExpect(jsonPath("$.content").value(source));
        body.put("contentFormat", "HTML");
        write(HttpMethod.POST, "/api/my/articles", author, body).andExpect(status().isBadRequest());
    }
}
