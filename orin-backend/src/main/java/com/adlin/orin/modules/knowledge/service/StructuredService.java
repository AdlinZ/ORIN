package com.adlin.orin.modules.knowledge.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

/**
 * Service interface for Structured Knowledge (Data Tables / SQL).
 * Handles CSV/Excel imports and Text-to-SQL generation.
 */
public interface StructuredService {

    /**
     * Upload a structured data file (CSV/Excel) and map it to a temporary DB table.
     *
     * @param file      The structured file
     * @param tableName Desired table name (validation required)
     * @param agentId   The Agent owning this data
     * @return Schema information of the created table
     */
    Map<String, Object> uploadDataTable(MultipartFile file, String tableName, String agentId);

    /**
     * Convert natural language query to SQL executable on the agent's data.
     *
     * @param agentId The Agent ID
     * @param query   Natural language query (e.g., "How many users signed up last
     *                week?")
     * @return Generated SQL statement
     */
    String generateSql(String agentId, String query);

    /**
     * Execute a SQL query directly (safe mode).
     *
     * @param agentId The Agent ID (for permission check)
     * @param sql     The SQL to execute
     * @return List of result rows
     */
    List<Map<String, Object>> executeQuery(String agentId, String sql);

    /**
     * Get schema info for all tables available to the agent.
     */
    List<Map<String, Object>> getDatabaseSchema(String agentId);
}
