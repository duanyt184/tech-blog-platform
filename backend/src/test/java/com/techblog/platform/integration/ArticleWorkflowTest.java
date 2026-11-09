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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
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
                "category", "Web开发", "tags", List.of("Java", "transactions"), "version", version);
    }
    JsonNode draft(MockHttpSession session) throws Exception {
        return tree(write(HttpMethod.POST, "/api/my/articles", session, payload(0, "# 实践\n正文\n```java\nint count = 1;\n```"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")));
    }
    JsonNode submit(JsonNode draft, MockHttpSession session) throws Exception {
        return tree(write(HttpMethod.POST, "/api/my/articles/" + draft.get("id").asLong() + "/submit", session,
                Map.of("version", draft.get("version").asLong())).andExpect(status().isOk()));
    }
    long userId(MockHttpSession session) throws Exception { return tree(mvc.perform(get("/api/auth/me").session(session))).get("id").asLong(); }
    JsonNode approve(JsonNode published) { return published; }

    @Test void draftPrivateAndOrdinaryTopicPublishesImmediately() throws Exception {
        var d=draft(author); long id=d.get("id").asLong();
        mvc.perform(get("/api/articles/"+id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/my/articles/"+id).session(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/editor/articles/"+id).session(editor)).andExpect(status().isNotFound());
        var a=submit(d,author);assertEquals("PUBLISHED",a.get("status").asText());assertTrue(a.get("community").asBoolean());
        mvc.perform(get("/api/articles/"+id)).andExpect(status().isOk());
        mvc.perform(get("/api/articles").param("q",a.get("title").asText())).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void publishedTopicIsEditableAndStaleAndForeignWritesFail() throws Exception {
        var a=submit(draft(author),author);long id=a.get("id").asLong();long version=a.get("version").asLong();
        write(HttpMethod.PUT,"/api/my/articles/"+id,other,payload(version,"stolen")).andExpect(status().isNotFound());
        write(HttpMethod.PUT,"/api/my/articles/"+id,author,payload(version,"new text")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        write(HttpMethod.PUT,"/api/my/articles/"+id,author,payload(version,"stale text")).andExpect(status().isConflict());
        var current=tree(mvc.perform(get("/api/my/articles/"+id).session(author)));
        write(HttpMethod.DELETE,"/api/my/articles/"+id+"?version="+current.get("version").asLong(),author,null).andExpect(status().isNoContent());
        mvc.perform(get("/api/articles/"+id)).andExpect(status().isNotFound());
    }
    @Test void csrfAndModeratorAndAdminAccessAreEnforced() throws Exception {
        mvc.perform(post("/api/my/articles").session(author).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload(0,"body")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/my/articles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/editor/moderation").session(author)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/rules").session(editor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/community/rules")).andExpect(status().isOk());
    }
    @Test void bookmarksAreIdempotentAndPrivate() throws Exception {
        var a=submit(draft(author),author);long id=a.get("id").asLong();
        write(HttpMethod.PUT,"/api/my/bookmarks/"+id,other,null).andExpect(status().isNoContent());
        write(HttpMethod.PUT,"/api/my/bookmarks/"+id,other,null).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/bookmarks").session(other)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/my/bookmarks").session(author)).andExpect(jsonPath("$.totalElements").value(0));
        write(HttpMethod.DELETE,"/api/my/bookmarks/"+id,other,null).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/bookmarks").session(other)).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void readingAndBookmarkingDoNotBumpTopicActivityOrVersion() throws Exception {
        var a=submit(draft(author),author);long id=a.get("id").asLong();
        entityManager.flush();entityManager.clear();
        jdbc.update("UPDATE articles SET updated_at='2000-01-01 00:00:00' WHERE id=?", id);
        var before=jdbc.queryForMap("SELECT updated_at,version,view_count FROM articles WHERE id=?",id);
        mvc.perform(get("/api/articles/"+id)).andExpect(status().isOk());
        write(HttpMethod.PUT,"/api/my/bookmarks/"+id,other,null).andExpect(status().isNoContent());
        entityManager.flush();entityManager.clear();
        var after=jdbc.queryForMap("SELECT updated_at,version,view_count,bookmark_count FROM articles WHERE id=?",id);
        assertEquals(before.get("updated_at"),after.get("updated_at"));
        assertEquals(before.get("version"),after.get("version"));
        assertEquals(((Number)before.get("view_count")).longValue()+1,((Number)after.get("view_count")).longValue());
        assertEquals(1L,((Number)after.get("bookmark_count")).longValue());
    }
    @Test void emptyPublicationRejectedAndMarkdownPreserved() throws Exception {
        var blank=tree(write(HttpMethod.POST,"/api/my/articles",author,payload(0,"")));
        write(HttpMethod.POST,"/api/my/articles/"+blank.get("id").asLong()+"/publish",author,Map.of("version",blank.get("version").asLong())).andExpect(status().isBadRequest());
        var data=new HashMap<String,Object>(payload(0,"# Hello **world**"));data.put("contentFormat","MARKDOWN");
        var a=submit(tree(write(HttpMethod.POST,"/api/my/articles",author,data)),author);
        mvc.perform(get("/api/articles/"+a.get("id").asLong())).andExpect(jsonPath("$.contentFormat").value("MARKDOWN")).andExpect(jsonPath("$.content").value("# Hello **world**"));
    }
    @Test void automaticDetectionQueuesButDoesNotGatePublication() throws Exception {
        var d=tree(write(HttpMethod.POST,"/api/my/articles",author,payload(0,"free crypto guaranteed profit")));
        var a=submit(d,author);assertEquals("PUBLISHED",a.get("status").asText());
        var queue=tree(mvc.perform(get("/api/editor/moderation").session(editor)).andExpect(status().isOk()));
        assertEquals(1,queue.size());assertEquals("AUTO",queue.get(0).get("source").asText());
        write(HttpMethod.POST,"/api/editor/moderation/"+queue.get(0).get("id").asLong()+"/resolve",editor,Map.of("action","DISMISS","note","false positive")).andExpect(status().isOk());
        mvc.perform(get("/api/articles/"+a.get("id").asLong())).andExpect(status().isOk());
    }
    @Test void topicReportsDeduplicateAndModeratorRemovesContent() throws Exception {
        var a=submit(draft(author),author);long id=a.get("id").asLong();
        write(HttpMethod.POST,"/api/articles/"+id+"/reports",other,Map.of("reason","spam")).andExpect(status().isOk());
        write(HttpMethod.POST,"/api/articles/"+id+"/reports",other,Map.of("reason","spam")).andExpect(status().isConflict());
        var queue=tree(mvc.perform(get("/api/editor/moderation").session(editor)));
        var path="/api/editor/moderation/"+queue.get(0).get("id").asLong()+"/resolve";
        write(HttpMethod.POST,path,editor,Map.of("action","DELETE","note","removed spam")).andExpect(status().isOk());
        mvc.perform(get("/api/articles/"+id)).andExpect(status().isNotFound());
        write(HttpMethod.POST,path,editor,Map.of("action","DISMISS","note","again")).andExpect(status().isConflict());
    }
    @Test void replyOwnershipLikesReportsAndActivityArePersistent() throws Exception {
        var a=submit(draft(author),author);long id=a.get("id").asLong();
        var reply=tree(write(HttpMethod.POST,"/api/articles/"+id+"/comments",other,Map.of("content","**Helpful** reply")).andExpect(status().isCreated()));long cid=reply.get("id").asLong();
        write(HttpMethod.PUT,"/api/comments/"+cid,author,Map.of("content","not mine")).andExpect(status().isForbidden());
        write(HttpMethod.PUT,"/api/comments/"+cid,other,Map.of("content","Edited reply")).andExpect(status().isOk());
        write(HttpMethod.PUT,"/api/comments/"+cid+"/likes",author,null).andExpect(jsonPath("$.count").value(1));
        write(HttpMethod.PUT,"/api/comments/"+cid+"/likes",author,null).andExpect(jsonPath("$.count").value(1));
        write(HttpMethod.POST,"/api/comments/"+cid+"/reports",author,Map.of("reason","off topic")).andExpect(status().isCreated());
        mvc.perform(get("/api/editor/moderation").session(editor)).andExpect(jsonPath("$[0].comment_id").value(cid));
        mvc.perform(get("/api/profiles/"+reply.get("authorId").asLong()+"/activity")).andExpect(jsonPath("$[0].type").value("REPLY"));
        mvc.perform(get("/api/articles/"+id+"/stats")).andExpect(jsonPath("$.replies").value(1));
    }
    @Test void advancedSearchAndParentCategoriesUsePersistedData() throws Exception {
        var admin=actor(UserRole.ADMIN);
        var parent=tree(write(HttpMethod.POST,"/api/admin/categories",admin,Map.of("name","Search parent","description","","color","#777770")));
        write(HttpMethod.POST,"/api/admin/categories",admin,Map.of("name","Search child","description","","color","#777770","parentId",parent.get("id").asLong())).andExpect(status().isCreated());
        var data=new HashMap<String,Object>(payload(0,"searchable body"));data.put("category","Search child");
        var a=submit(tree(write(HttpMethod.POST,"/api/my/articles",author,data)),author);
        mvc.perform(get("/api/articles").param("category","Search parent").param("author",a.get("author").asText()).param("replies","no")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/articles").param("replies","yes")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/articles").param("after","invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles").param("after","2026-12-01").param("before","2026-01-01")).andExpect(status().isBadRequest());
    }
    @Test void tagsRulesAndCategoryHierarchyAreAdminManaged() throws Exception {
        var admin=actor(UserRole.ADMIN);
        write(HttpMethod.POST,"/api/admin/tags",admin,Map.of("name","test-tag","description","Testing")).andExpect(status().isOk());
        write(HttpMethod.POST,"/api/admin/rules",admin,Map.of("title","Custom rule","description","Be helpful","pattern","blocked-phrase","enabled",true)).andExpect(status().isOk());
        var d=tree(write(HttpMethod.POST,"/api/my/articles",author,payload(0,"blocked-phrase")));submit(d,author);
        mvc.perform(get("/api/editor/moderation").session(editor)).andExpect(jsonPath("$[0].reason").value("Custom rule"));
        var parent=tree(write(HttpMethod.POST,"/api/admin/categories",admin,Map.of("name","Parent","description","","color","#123456")));
        write(HttpMethod.PUT,"/api/admin/categories/"+parent.get("id").asLong(),admin,Map.of("name","Parent","description","","color","#123456","parentId",parent.get("id").asLong())).andExpect(status().isBadRequest());
    }
    @Test void profilesArePersistedAndDoNotExposeEmailOrCredentials() throws Exception {
        long id=userId(author);
        write(HttpMethod.PUT,"/api/my/profile",author,Map.of("displayName","技术作者","bio","分享实践"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("技术作者"));
        entityManager.flush(); entityManager.clear();
        mvc.perform(get("/api/profiles/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.bio").value("分享实践"))
            .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        write(HttpMethod.PUT,"/api/my/profile",author,Map.of("displayName","x".repeat(81),"bio","" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/profiles/"+userId(other))).andExpect(jsonPath("$.bio").value(""));
    }

    @Test void likesAreUniquePrivateDraftsCannotBeLikedAndUnlikeIsIdempotent() throws Exception {
        var d=draft(author); long id=d.get("id").asLong();
        write(HttpMethod.PUT,"/api/articles/"+id+"/likes",other,null).andExpect(status().isNotFound());
        approve(submit(d,author));
        mvc.perform(put("/api/articles/"+id+"/likes").session(other)).andExpect(status().isForbidden());
        write(HttpMethod.PUT,"/api/articles/"+id+"/likes",other,null).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        write(HttpMethod.PUT,"/api/articles/"+id+"/likes",other,null).andExpect(jsonPath("$.count").value(1));
        write(HttpMethod.PUT,"/api/articles/"+id+"/likes",author,null).andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/api/articles/"+id+"/likes")).andExpect(jsonPath("$.count").value(2)).andExpect(jsonPath("$.liked").value(false));
        write(HttpMethod.DELETE,"/api/articles/"+id+"/likes",other,null).andExpect(jsonPath("$.count").value(1));
        write(HttpMethod.DELETE,"/api/articles/"+id+"/likes",other,null).andExpect(jsonPath("$.count").value(1));
    }

    @Test void categoriesAreAdminOnlyAndCannotBeDeletedWhileInUse() throws Exception {
        var admin=actor(UserRole.ADMIN);
        var payload=Map.of("name","测试分类","description","说明","color","#123456");
        write(HttpMethod.POST,"/api/admin/categories",author,payload).andExpect(status().isForbidden());
        write(HttpMethod.POST,"/api/admin/categories",editor,payload).andExpect(status().isForbidden());
        var cat=tree(write(HttpMethod.POST,"/api/admin/categories",admin,payload).andExpect(status().isCreated()));
        long id=cat.get("id").asLong();
        write(HttpMethod.POST,"/api/admin/categories",admin,payload).andExpect(status().isConflict());
        write(HttpMethod.PUT,"/api/admin/categories/"+id,admin,Map.of("name","测试分类","description","新说明","color","#aabbcc")).andExpect(status().isOk());
        var articleBody=new HashMap<String,Object>(payload(0,"内容"));articleBody.put("category","测试分类");
        var article=tree(write(HttpMethod.POST,"/api/my/articles",author,articleBody).andExpect(status().isCreated()));
        write(HttpMethod.DELETE,"/api/admin/categories/"+id,admin,null).andExpect(status().isConflict());
        write(HttpMethod.DELETE,"/api/my/articles/"+article.get("id").asLong()+"?version="+article.get("version").asLong(),author,null).andExpect(status().isNoContent());
        write(HttpMethod.DELETE,"/api/admin/categories/"+id,admin,null).andExpect(status().isNoContent());
    }

    @Test void roleChangesAndAccountSuspensionApplyToExistingSessions() throws Exception {
        var admin=actor(UserRole.ADMIN); long target=userId(editor);
        mvc.perform(get("/api/admin/users").session(author)).andExpect(status().isForbidden());
        write(HttpMethod.PUT,"/api/admin/users/"+target,admin,Map.of("role","USER","enabled",true)).andExpect(status().isNoContent());
        mvc.perform(get("/api/editor/articles").session(editor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(editor)).andExpect(jsonPath("$.role").value("USER"));
        long own=userId(admin);
        write(HttpMethod.PUT,"/api/admin/users/"+own,admin,Map.of("role","USER","enabled",true)).andExpect(status().isConflict());
        String name=users.findById(target).orElseThrow().getUsername();
        write(HttpMethod.PUT,"/api/admin/users/"+target,admin,Map.of("role","USER","enabled",false)).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/articles").session(editor)).andExpect(status().isUnauthorized());
        write(HttpMethod.POST,"/api/auth/login",new MockHttpSession(),Map.of("identifier",name,"password","password123")).andExpect(status().isUnauthorized());
    }

    @Test void adminCanModerateArticlesAndCommentsButCannotReadDrafts() throws Exception {
        var admin=actor(UserRole.ADMIN);var d=draft(author);long id=d.get("id").asLong();
        mvc.perform(get("/api/editor/articles/"+id).session(admin)).andExpect(status().isNotFound());
        write(HttpMethod.DELETE,"/api/admin/articles/"+id+"?version="+d.get("version").asLong(),admin,null).andExpect(status().isForbidden());
        var published=approve(submit(d,author));
        var comment=tree(write(HttpMethod.POST,"/api/articles/"+id+"/comments",other,Map.of("content","待管理评论")).andExpect(status().isCreated()));
        mvc.perform(get("/api/admin/comments").session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        write(HttpMethod.DELETE,"/api/comments/"+comment.get("id").asLong(),admin,null).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/comments").session(admin)).andExpect(jsonPath("$.totalElements").value(0));
        write(HttpMethod.DELETE,"/api/admin/articles/"+id+"?version="+published.get("version").asLong(),author,null).andExpect(status().isForbidden());
        write(HttpMethod.DELETE,"/api/admin/articles/"+id+"?version="+published.get("version").asLong(),admin,null).andExpect(status().isNoContent());
        mvc.perform(get("/api/articles/"+id)).andExpect(status().isNotFound());
    }

    @Test void publicProfilesAndDirectoryNeverRevealPrivateAccountFields() throws Exception {
        var a=submit(draft(author),author);
        var summary=tree(mvc.perform(get("/api/profiles/"+a.get("authorId").asLong()+"/summary")).andExpect(status().isOk()));
        assertEquals(1,summary.get("stats").get("topics").asInt());
        var directory=tree(mvc.perform(get("/api/community/users").param("q",a.get("author").asText())).andExpect(status().isOk()));
        assertEquals(1,directory.size());assertFalse(directory.get(0).has("email"));assertFalse(directory.get(0).has("password"));
        mvc.perform(get("/api/articles").param("q","a%_not_a_user")).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void replySearchAndDeletionDoNotExposeHiddenContent() throws Exception {
        var a=submit(draft(author),author);long id=a.get("id").asLong();
        var c=tree(write(HttpMethod.POST,"/api/articles/"+id+"/comments",other,Map.of("content","unique-reply-search-term")));
        mvc.perform(get("/api/articles").param("q","unique-reply-search-term")).andExpect(jsonPath("$.totalElements").value(1));
        write(HttpMethod.DELETE,"/api/comments/"+c.get("id").asLong(),other,null).andExpect(status().isNoContent());
        mvc.perform(get("/api/articles").param("q","unique-reply-search-term")).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void ruleDetectionAlsoRunsOnPublishedEditsAndTagGroupsPersist() throws Exception {
        var admin=actor(UserRole.ADMIN);
        write(HttpMethod.POST,"/api/admin/tags",admin,Map.of("name","example-tag","description","Example","groupName","Workflows")).andExpect(status().isOk());
        var tags=tree(mvc.perform(get("/api/community/tags")));
        boolean found=false;for(var tag:tags)if(tag.get("name").asText().equals("example-tag")){found=true;assertEquals("Workflows",tag.get("groupName").asText());}assertTrue(found);
        var a=submit(draft(author),author);
        write(HttpMethod.PUT,"/api/my/articles/"+a.get("id").asLong(),author,payload(a.get("version").asLong(),"free crypto")).andExpect(status().isOk());
        mvc.perform(get("/api/editor/moderation").session(editor)).andExpect(jsonPath("$[0].source").value("AUTO"));
    }
}
