package com.adlin.orin.modules.knowledge.component;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import java.util.List;
import java.util.Map;

public interface VectorStoreProvider {
    /**
     * Add documents to the vector store
     */
    void addDocuments(String collectionName, List<KnowledgeDocument> documents);

    /**
     * Delete documents from the vector store
     */
    void deleteDocuments(String collectionName, List<String> docIds);

    /**
     * Search for similar documents
     * 
     * @param query The search query
     * @param k     Number of results to return
     * @return List of matches with scores
     */
    List<SearchResult> search(String collectionName, String query, int k);

    /**
     * Search for similar documents with specific embedding model
     */
    default List<SearchResult> search(String collectionName, String query, int k, String embeddingModel) {
        return search(collectionName, query, k);
    }

    /**
     * Get chunks for a specific document
     */
    List<DocumentChunk> getDocumentChunks(String collectionName, String docId);

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    class SearchResult {
        private String content;
        private Double score;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    class DocumentChunk {
        private String id;
        private String content;
        private List<Double> vector;
        private Map<String, Object> metadata;
    }
}
