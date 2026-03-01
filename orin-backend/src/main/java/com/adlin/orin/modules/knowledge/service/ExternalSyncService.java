package com.adlin.orin.modules.knowledge.service;

import java.util.List;
import java.util.Map;

/**
 * Service interface for external knowledge source synchronization.
 * Supports Notion, Web URLs, and Database connections.
 */
public interface ExternalSyncService {

    /**
     * Sync from Notion workspace
     *
     * @param kbId      Knowledge base ID
     * @param config    Notion configuration (integrationToken, databaseId)
     * @return Sync result with document count
     */
    Map<String, Object> syncFromNotion(String kbId, Map<String, String> config);

    /**
     * Sync from Web URLs
     *
     * @param kbId      Knowledge base ID
     * @param config    Web sync configuration (urls, crawlDepth, etc.)
     * @return Sync result with document count
     */
    Map<String, Object> syncFromWeb(String kbId, Map<String, Object> config);

    /**
     * Connect to external database
     *
     * @param kbId      Knowledge base ID
     * @param config    Database configuration (connectionString, username, password, etc.)
     * @return Connection result with schema info
     */
    Map<String, Object> connectDatabase(String kbId, Map<String, String> config);

    /**
     * Test database connection
     *
     * @param config Database configuration
     * @return Test result
     */
    Map<String, Object> testDatabaseConnection(Map<String, String> config);

    /**
     * Get database schema for a connected KB
     *
     * @param kbId Knowledge base ID
     * @return Schema information
     */
    List<Map<String, Object>> getDatabaseSchema(String kbId);

    /**
     * Sync database table as documents
     *
     * @param kbId      Knowledge base ID
     * @param tableName Table name to sync
     * @return Sync result
     */
    Map<String, Object> syncDatabaseTable(String kbId, String tableName);

    /**
     * Test Notion connection
     *
     * @param config Notion configuration
     * @return Test result
     */
    Map<String, Object> testNotionConnection(Map<String, String> config);

    /**
     * List available Notion databases
     *
     * @param config Notion configuration
     * @return List of databases
     */
    List<Map<String, String>> listNotionDatabases(Map<String, String> config);

    /**
     * Test Web URL connectivity
     *
     * @param url URL to test
     * @return Test result with page info
     */
    Map<String, Object> testWebUrl(String url);

    /**
     * Test RAGFlow connection
     *
     * @param config RAGFlow configuration (endpointUrl, apiKey)
     * @return Test result
     */
    Map<String, Object> testRAGFlowConnection(Map<String, String> config);

    /**
     * List RAGFlow knowledge bases
     *
     * @param config RAGFlow configuration
     * @return List of knowledge bases
     */
    List<Map<String, Object>> listRAGFlowKnowledgeBases(Map<String, String> config);

    /**
     * Sync from RAGFlow knowledge base
     *
     * @param kbId      ORIN Knowledge base ID
     * @param config    RAGFlow configuration (endpointUrl, apiKey, ragflowKbId)
     * @return Sync result
     */
    Map<String, Object> syncFromRAGFlow(String kbId, Map<String, String> config);

    /**
     * Upload document to RAGFlow
     *
     * @param kbId          ORIN Knowledge base ID
     * @param config        RAGFlow configuration
     * @param fileName      File name
     * @param fileContent   File content
     * @return Upload result
     */
    Map<String, Object> uploadToRAGFlow(String kbId, Map<String, String> config, String fileName, byte[] fileContent);

    /**
     * Retrieval test from RAGFlow
     *
     * @param config    RAGFlow configuration
     * @param kbId      RAGFlow knowledge base ID
     * @param query     Query string
     * @param topK      Number of results
     * @return Retrieval results
     */
    List<Map<String, Object>> retrievalFromRAGFlow(Map<String, String> config, String kbId, String query, int topK);
}
