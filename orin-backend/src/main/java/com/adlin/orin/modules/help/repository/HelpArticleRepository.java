package com.adlin.orin.modules.help.repository;

import com.adlin.orin.modules.help.entity.HelpArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HelpArticleRepository extends JpaRepository<HelpArticle, Long> {

    /**
     * 获取启用的文档列表
     */
    Page<HelpArticle> findByEnabledTrue(Pageable pageable);

    /**
     * 按分类获取文档列表
     */
    List<HelpArticle> findByCategoryAndEnabledTrueOrderBySortOrder(String category);

    /**
     * 按分类分页获取文档
     */
    Page<HelpArticle> findByCategoryAndEnabledTrue(String category, Pageable pageable);

    /**
     * 搜索文档
     */
    @Query("SELECT h FROM HelpArticle h WHERE h.enabled = true AND (h.title LIKE %:keyword% OR h.content LIKE %:keyword%)")
    Page<HelpArticle> search(String keyword, Pageable pageable);

    /**
     * 获取所有分类
     */
    @Query("SELECT DISTINCT h.category FROM HelpArticle h WHERE h.enabled = true")
    List<String> findAllCategories();

    /**
     * 增加阅读次数
     */
    @Modifying
    @Query("UPDATE HelpArticle h SET h.viewCount = h.viewCount + 1 WHERE h.id = :id")
    void incrementViewCount(Long id);

    /**
     * 按标题查找
     */
    Optional<HelpArticle> findByTitle(String title);

    /**
     * 按页面路径查找相关文档
     */
    List<HelpArticle> findByPagePathAndEnabledTrue(String pagePath);
}
