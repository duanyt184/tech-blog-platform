package com.techblog.platform.service;

import com.techblog.platform.dto.ArticleResponse;
import com.techblog.platform.dto.CreateArticleRequest;
import com.techblog.platform.entity.Article;
import com.techblog.platform.entity.User;
import com.techblog.platform.exception.NotFoundException;
import com.techblog.platform.repository.ArticleRepository;
import com.techblog.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章业务逻辑（Issue #4 发布；后续 #3 列表/详情、#5 个人管理也在此扩展）。
 */
@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    public ArticleService(ArticleRepository articleRepository, UserRepository userRepository) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
    }

    /**
     * 发布一篇纯文本文章。
     *
     * <p>作者来自已认证的当前用户（由 Controller 传入 authorId），不信任前端提交的作者字段，
     * 避免冒充他人身份发布。MVP v0.1 阶段发布后直接进入 {@link com.techblog.platform.entity.ArticleStatus#PUBLISHED}，
     * 以便「文章广场 / 详情」能立刻看到。</p>
     */
    @Transactional
    public ArticleResponse publish(Long authorId, CreateArticleRequest request) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        Article article = Article.create(author, request.title().trim(), request.content().trim());
        Article saved = articleRepository.save(article);
        return ArticleResponse.from(saved);
    }
}
