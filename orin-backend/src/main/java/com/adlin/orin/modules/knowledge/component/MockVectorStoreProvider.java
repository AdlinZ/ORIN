package com.adlin.orin.modules.knowledge.component;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class MockVectorStoreProvider implements VectorStoreProvider {

    @Override
    public void addDocuments(String collectionName, List<KnowledgeDocument> documents) {
        // Mock implementation: do nothing or log
        System.out.println("Mock adding documents to collection: " + collectionName);
    }

    @Override
    public void deleteDocuments(String collectionName, List<String> docIds) {
        System.out.println("Mock deleting documents from collection: " + collectionName);
    }

    @Override
    public List<SearchResult> search(String collectionName, String query, int k) {
        // Return dummy results
        System.out.println("Mock searching collection: " + collectionName + " for query: " + query);
        List<SearchResult> results = new ArrayList<>();
        results.add(SearchResult.builder()
                .content("Mock result for: " + query)
                .score(0.95)
                .metadata(Collections.emptyMap())
                .build());
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
