package com.adlin.orin.modules.help.controller;

import com.adlin.orin.modules.help.entity.HelpArticle;
import com.adlin.orin.modules.help.service.HelpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 帮助中心控制器
 */
@RestController
@RequestMapping("/api/v1/help")
@RequiredArgsConstructor
@Tag(name = "Help Center", description = "帮助中心")
public class HelpController {

    private final HelpService helpService;

    @Operation(summary = "获取文档列表")
    @GetMapping("/articles")
    public ResponseEntity<Page<HelpArticle>> getArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(helpService.getArticles(page, size));
    }

    @Operation(summary = "按分类获取文档")
    @GetMapping("/articles/category/{category}")
    public ResponseEntity<Page<HelpArticle>> getArticlesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(helpService.getArticlesByCategory(category, page, size));
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/articles/{id}")
    public ResponseEntity<HelpArticle> getArticle(@PathVariable Long id) {
        return helpService.getArticleAndIncrementView(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "搜索文档")
    @GetMapping("/articles/search")
    public ResponseEntity<Page<HelpArticle>> searchArticles(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(helpService.search(keyword, page, size));
    }

    @Operation(summary = "获取所有分类")
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(helpService.getCategories());
    }

    @Operation(summary = "获取分类统计")
    @GetMapping("/categories/stats")
    public ResponseEntity<Map<String, Long>> getCategoryStats() {
        return ResponseEntity.ok(helpService.getCategoryStats());
    }

    @Operation(summary = "创建文档")
    @PostMapping("/articles")
    public ResponseEntity<HelpArticle> createArticle(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");
        String category = request.get("category");
        String tags = request.get("tags");

        HelpArticle article = helpService.createArticle(title, content, category, tags);
        return ResponseEntity.ok(article);
    }

    @Operation(summary = "更新文档")
    @PutMapping("/articles/{id}")
    public ResponseEntity<HelpArticle> updateArticle(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");
        String category = request.get("category");
        String tags = request.get("tags");

        HelpArticle article = helpService.updateArticle(id, title, content, category, tags);
        return ResponseEntity.ok(article);
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/articles/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable Long id) {
        helpService.deleteArticle(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "启用/禁用文档")
    @PostMapping("/articles/{id}/toggle")
    public ResponseEntity<HelpArticle> toggleArticle(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        HelpArticle article = helpService.toggleEnabled(id, enabled);
        return ResponseEntity.ok(article);
    }
}
