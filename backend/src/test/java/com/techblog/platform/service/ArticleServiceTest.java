package com.techblog.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techblog.platform.dto.ArticleResponse;
import com.techblog.platform.dto.CreateArticleRequest;
import com.techblog.platform.entity.Article;
import com.techblog.platform.entity.ArticleStatus;
import com.techblog.platform.entity.User;
import com.techblog.platform.exception.NotFoundException;
import com.techblog.platform.repository.ArticleRepository;
import com.techblog.platform.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserRepository userRepository;

    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        articleService = new ArticleService(articleRepository, userRepository);
    }

    @Test
    @DisplayName("发布：作者来自当前登录用户，标题与正文去首尾空格，状态为 PUBLISHED")
    void shouldPublishArticleWithCurrentUserAsAuthor() {
        User author = User.create("alice", "alice@example.com", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            // 模拟 JPA 回填自增主键，方便断言响应里的 id
            setId(article, 10L);
            return article;
        });

        ArticleResponse response = articleService.publish(1L,
                new CreateArticleRequest("  第一篇博客  ", "  正文内容  "));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());
        Article saved = captor.getValue();

        assertThat(saved.getAuthor()).isEqualTo(author);
        assertThat(saved.getTitle()).isEqualTo("第一篇博客");
        assertThat(saved.getContent()).isEqualTo("正文内容");
        assertThat(saved.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(saved.getPublishedAt()).isNotNull();

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(response.author().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("发布：当前用户不存在时抛出 404 语义异常")
    void shouldThrowWhenAuthorNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.publish(999L,
                new CreateArticleRequest("标题", "正文")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("用户不存在");
    }

    /** 用反射回填 id，模拟数据库自增，避免污染 Article 实体（它没有 id setter）。 */
    private void setId(Article article, Long id) {
        try {
            java.lang.reflect.Field field = Article.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(article, id);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
