package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * Service interface for Unstructured Knowledge (Documents).
 * Handles the RAG pipeline: Parse -> Slice -> Embed -> Store.
 */
public interface UnstructuredService {

    /**
     * Upload and process a raw file (PDF, Markdown, etc.).
     * Triggers the parsing and slicing pipeline asynchronously.
     *
     * @param file    The raw file uploaded by user
     * @param kbId    Target Knowledge Base ID
     * @param agentId The Agent owning this knowledge
     * @return The created KnowledgeDocument entity (status may be PROCESSING)
     */
    KnowledgeDocument uploadDocument(MultipartFile file, String kbId, String agentId);

    /**
     * Re-index a specific document (e.g. after changing slicing rules).
     */
    void reIndexDocument(String documentId);

    /**
     * Retrieve relevant context chunks for a given query.
     *
     * @param agentId Target Agent ID to scope the search
     * @param query   User query
     * @param limit   Max number of chunks
     * @return List of text chunks with metadata
     */
    List<String> retrieveContext(String agentId, String query, int limit);

    /**
     * Delete a document and its vector embeddings.
     */
    void deleteDocument(String documentId);
}
