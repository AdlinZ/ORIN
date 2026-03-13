package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.dto.KeywordTag;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 关键词提取服务
 * 支持动态从知识库提取关键词和静态配置关键词
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordExtractService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;

    // 内存缓存，TTL 5分钟
    private final Map<String, List<KeywordTag>> keywordCache = new ConcurrentHashMap<>();
    private long lastCacheTime = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5分钟

    // 静态标签库（默认配置）
    private static final List<String> DEFAULT_TAGS = Arrays.asList(
            "知识库检索", "语义理解", "逻辑推理", "DeepSeek-R1",
            "Agent_Thinking", "文本纠错", "跨库关联", "用户建模",
            "RAG", "向量检索", "混合搜索", "智能问答"
    );

    // 颜色配置
    private static final List<String> TAG_COLORS = Arrays.asList(
            "#00BFA5", "#26FFDF", "#10b981", "#3b82f6",
            "#64748b", "#94a3b8", "#f59e0b", "#ef4444"
    );

    /**
     * 获取首页展示的热门关键词
     */
    public List<KeywordTag> getPopularKeywords(int limit) {
        // 检查缓存
        if (!keywordCache.isEmpty() && System.currentTimeMillis() - lastCacheTime < CACHE_TTL_MS) {
            return keywordCache.getOrDefault("popular", Collections.emptyList()).stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        // 提取关键词
        List<KeywordTag> keywords = extractKeywordsFromKnowledgeBase(null, limit * 2);

        // 如果动态提取的关键词不足，使用静态标签补充
        if (keywords.size() < limit) {
            keywords.addAll(getStaticKeywords(limit - keywords.size()));
        }

        // 更新缓存
        keywordCache.put("popular", keywords);
        lastCacheTime = System.currentTimeMillis();

        return keywords.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 从指定知识库提取关键词
     */
    public List<KeywordTag> extractKeywordsFromKnowledgeBase(String datasetId, int limit) {
        List<KeywordTag> keywords = new ArrayList<>();

        try {
            List<KnowledgeBase> knowledgeBases;
            if (datasetId != null && !datasetId.isEmpty()) {
                knowledgeBases = knowledgeBaseRepository.findById(datasetId).map(List::of).orElseGet(knowledgeBaseRepository::findAll);
            } else {
                knowledgeBases = knowledgeBaseRepository.findAll();
            }

            // 统计词频
            Map<String, Integer> wordFrequency = new HashMap<>();

            for (KnowledgeBase kb : knowledgeBases) {
                // 添加知识库名称（中等优先级）
                if (kb.getName() != null) {
                    wordFrequency.merge(kb.getName(), 3, (a, b) -> a + b);
                }
                // 添加知识库描述（最高优先级）
                if (kb.getDescription() != null) {
                    extractWordsFromText(kb.getDescription(), wordFrequency, 5);
                }

                // 获取该知识库下的文档（低优先级）
                List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(kb.getId());
                for (KnowledgeDocument doc : docs) {
                    // 添加文档标题（最低优先级）
                    if (doc.getFileName() != null) {
                        wordFrequency.merge(doc.getFileName(), 1, (a, b) -> a + b);
                    }
                }
            }

            // 按词频排序，取前N个
            List<Map.Entry<String, Integer>> sorted = wordFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(limit)
                    .collect(Collectors.toList());

            // 转换为KeywordTag
            double maxFreq = sorted.isEmpty() ? 1.0 : sorted.get(0).getValue();
            Random random = new Random();

            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Integer> entry = sorted.get(i);
                double weight = entry.getValue() / maxFreq;

                keywords.add(KeywordTag.builder()
                        .label(entry.getKey())
                        .weight(weight)
                        .opacity(0.4 + weight * 0.4) // 0.4-0.8
                        .size(9 + (int) (weight * 5)) // 9-14px
                        .color(TAG_COLORS.get(i % TAG_COLORS.size()))
                        .delay(random.nextDouble() * 2) // 0-2秒延迟
                        .build());
            }

        } catch (Exception e) {
            log.error("提取关键词失败", e);
            // 返回静态标签作为降级
            return getStaticKeywords(limit);
        }

        return keywords;
    }

    /**
     * 获取搜索联想建议
     */
    public List<KeywordTag> getSearchSuggestions(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getPopularKeywords(limit);
        }

        List<KeywordTag> suggestions = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();

        // 从知识库名称和文档标题中匹配
        List<KnowledgeBase> knowledgeBases = knowledgeBaseRepository.findAll();
        Map<String, Integer> matchedWords = new HashMap<>();

        for (KnowledgeBase kb : knowledgeBases) {
            if (kb.getName() != null && kb.getName().toLowerCase().contains(lowerKeyword)) {
                matchedWords.merge(kb.getName(), 5, (a, b) -> a + b);
            }

            List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(kb.getId());
            for (KnowledgeDocument doc : docs) {
                if (doc.getFileName() != null && doc.getFileName().toLowerCase().contains(lowerKeyword)) {
                    matchedWords.merge(doc.getFileName(), 3, (a, b) -> a + b);
                }
            }
        }

        // 匹配静态标签
        for (String tag : DEFAULT_TAGS) {
            if (tag.toLowerCase().contains(lowerKeyword)) {
                matchedWords.merge(tag, 2, (a, b) -> a + b);
            }
        }

        // 排序并转换
        List<Map.Entry<String, Integer>> sorted = matchedWords.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());

        double maxFreq = sorted.isEmpty() ? 1.0 : sorted.get(0).getValue();
        Random random = new Random();

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            double weight = entry.getValue() / maxFreq;

            suggestions.add(KeywordTag.builder()
                    .label(entry.getKey())
                    .weight(weight)
                    .opacity(0.4 + weight * 0.4)
                    .size(9 + (int) (weight * 5))
                    .color(TAG_COLORS.get(i % TAG_COLORS.size()))
                    .delay(random.nextDouble() * 1.5)
                    .build());
        }

        return suggestions;
    }

    /**
     * 获取静态配置的关键词
     */
    private List<KeywordTag> getStaticKeywords(int limit) {
        Random random = new Random();
        List<KeywordTag> tags = new ArrayList<>();

        for (int i = 0; i < Math.min(limit, DEFAULT_TAGS.size()); i++) {
            double weight = 0.5 + random.nextDouble() * 0.5;
            tags.add(KeywordTag.builder()
                    .label(DEFAULT_TAGS.get(i))
                    .weight(weight)
                    .opacity(weight)
                    .size(10 + (int) (weight * 6))
                    .color(TAG_COLORS.get(i % TAG_COLORS.size()))
                    .delay(random.nextDouble() * 2)
                    .build());
        }

        return tags;
    }

    /**
     * 从文本中提取词汇
     */
    private void extractWordsFromText(String text, Map<String, Integer> wordFrequency, int weight) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // 简单分词（按空格和标点符号分割）
        String[] words = text.split("[\\s,，。、！？;；:：]+");
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 2 && word.length() <= 10) {
                wordFrequency.merge(word, weight, (a, b) -> a + b);
            }
        }
    }

    /**
     * 从文本中提取词汇（默认权重为1）
     */
    private void extractWordsFromText(String text, Map<String, Integer> wordFrequency) {
        extractWordsFromText(text, wordFrequency, 1);
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        keywordCache.clear();
        lastCacheTime = 0;
    }
}
