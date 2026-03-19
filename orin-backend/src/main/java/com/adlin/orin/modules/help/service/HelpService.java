package com.adlin.orin.modules.help.service;

import com.adlin.orin.modules.help.entity.HelpArticle;
import com.adlin.orin.modules.help.repository.HelpArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 帮助中心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelpService {

    private final HelpArticleRepository articleRepository;

    /**
     * 获取文档列表
     */
    public Page<HelpArticle> getArticles(int page, int size) {
        return articleRepository.findByEnabledTrue(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sortOrder")));
    }

    /**
     * 按分类获取文档列表
     */
    public List<HelpArticle> getArticlesByCategory(String category) {
        return articleRepository.findByCategoryAndEnabledTrueOrderBySortOrder(category);
    }

    /**
     * 按分类分页获取文档
     */
    public Page<HelpArticle> getArticlesByCategory(String category, int page, int size) {
        return articleRepository.findByCategoryAndEnabledTrue(category,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sortOrder")));
    }

    /**
     * 获取文档详情
     */
    public Optional<HelpArticle> getArticle(Long id) {
        return articleRepository.findById(id);
    }

    /**
     * 获取文档详情并增加阅读次数
     */
    @Transactional
    public Optional<HelpArticle> getArticleAndIncrementView(Long id) {
        articleRepository.incrementViewCount(id);
        return articleRepository.findById(id);
    }

    /**
     * 搜索文档
     */
    public Page<HelpArticle> search(String keyword, int page, int size) {
        return articleRepository.search(keyword, PageRequest.of(page, size));
    }

    /**
     * 获取所有分类
     */
    public List<String> getCategories() {
        return articleRepository.findAllCategories();
    }

    /**
     * 获取分类统计
     */
    public Map<String, Long> getCategoryStats() {
        List<String> categories = articleRepository.findAllCategories();
        return categories.stream()
                .collect(java.util.stream.Collectors.toMap(
                        c -> c,
                        c -> (long) articleRepository.findByCategoryAndEnabledTrueOrderBySortOrder(c).size()
                ));
    }

    /**
     * 创建文档
     */
    @Transactional
    public HelpArticle createArticle(String title, String content, String category, String tags) {
        HelpArticle article = HelpArticle.builder()
                .title(title)
                .content(content)
                .category(category)
                .tags(tags)
                .enabled(true)
                .viewCount(0)
                .build();

        return articleRepository.save(article);
    }

    /**
     * 更新文档
     */
    @Transactional
    public HelpArticle updateArticle(Long id, String title, String content, String category, String tags) {
        return articleRepository.findById(id)
                .map(article -> {
                    if (title != null) article.setTitle(title);
                    if (content != null) article.setContent(content);
                    if (category != null) article.setCategory(category);
                    if (tags != null) article.setTags(tags);
                    return articleRepository.save(article);
                })
                .orElseThrow(() -> new RuntimeException("Article not found: " + id));
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteArticle(Long id) {
        articleRepository.deleteById(id);
    }

    /**
     * 启用/禁用文档
     */
    @Transactional
    public HelpArticle toggleEnabled(Long id, boolean enabled) {
        return articleRepository.findById(id)
                .map(article -> {
                    article.setEnabled(enabled);
                    return articleRepository.save(article);
                })
                .orElseThrow(() -> new RuntimeException("Article not found: " + id));
    }
}
