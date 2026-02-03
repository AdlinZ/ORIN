package com.adlin.orin.modules.knowledge.component;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@org.springframework.context.annotation.Primary
public class MockVectorStoreProvider implements VectorStoreProvider {

    @Override
    public void addDocuments(String collectionName, List<KnowledgeDocument> documents) {
        // Mock implementation: do nothing or log
        System.out.println("Mock adding documents to collection: " + collectionName);
    }

    @Override
    public void addChunks(String kbId, List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> chunks) {
        System.out.println("Mock adding " + chunks.size() + " chunks to KB: " + kbId);
    }

    @Override
    public void deleteDocuments(String collectionName, List<String> docIds) {
        System.out.println("Mock deleting documents from collection: " + collectionName);
    }

    @Override
    public void deleteKnowledgeBase(String kbId) {
        System.out.println("Mock deleting entire knowledge base: " + kbId);
    }

    @Override
    public List<SearchResult> search(String collectionName, String query, int k) {
        // Return dummy results
        System.out.println("Mock searching collection: " + collectionName + " for query: " + query);
        List<SearchResult> results = new ArrayList<>();

        // Return dynamic results based on query for better demo experience
        if (query != null && query.toLowerCase().contains("dify")) {
            results.add(SearchResult.builder()
                    .content("Dify 是一个开源的 LLM 应用开发平台。接入 Dify 需要获取 API Key 和 API Base URL。")
                    .score(0.92)
                    .metadata(Collections.singletonMap("source", "dify_docs.md"))
                    .build());
            results.add(SearchResult.builder()
                    .content("在 ORIN 中配置 Dify 接入，请前往系统设置 -> 模型设置页面。")
                    .score(0.85)
                    .metadata(Collections.singletonMap("page", "12"))
                    .build());
        } else {
            for (int i = 1; i <= k; i++) {
                results.add(SearchResult.builder()
                        .content("这是一个模拟的检索结果片段 " + i + "，针对查询: " + query + "。\n这里包含了一些相关的上下文信息，用于测试 RAG 检索效果。")
                        .score(0.95 - (i * 0.1))
                        .metadata(Collections.singletonMap("chunk_id", "chunk_" + i))
                        .build());
            }
        }

        return results;
    }

    @Override
    public List<DocumentChunk> getDocumentChunks(String collectionName, String docId) {
        System.out.println("Mock getting chunks for doc: " + docId);
        List<DocumentChunk> chunks = new ArrayList<>();
        chunks.add(DocumentChunk.builder()
                .id("chunk-1")
                .content("This is the first chunk of document " + docId)
                .vector(List.of(0.1, 0.2, 0.3))
                .build());
        return chunks;
    }
}
