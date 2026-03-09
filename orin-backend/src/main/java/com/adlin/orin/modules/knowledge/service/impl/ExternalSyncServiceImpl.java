package com.adlin.orin.modules.knowledge.service.impl;

import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeType;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.service.ExternalSyncService;
import com.adlin.orin.modules.knowledge.service.RAGFlowIntegrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.sql.*;
import java.util.*;

/**
 * Implementation of ExternalSyncService for Notion, Web, and Database sync.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalSyncServiceImpl implements ExternalSyncService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RAGFlowIntegrationService ragflowIntegrationService;

    // Store active database connections per KB
    private final Map<String, Connection> dbConnections = new HashMap<>();

    // Store database configs per KB
    private final Map<String, Map<String, String>> dbConfigs = new HashMap<>();

    @Value("${spring.datasource.url:jdbc:h2:mem:orin}")
    private String defaultDbUrl;

    // Jina Reader Configuration
    @Value("${jina.reader.enabled:true}")
    private boolean jinaReaderEnabled;

    @Value("${jina.reader.api-key:}")
    private String jinaReaderApiKey;

    // ==================== Notion Sync ====================

    @Override
    public Map<String, Object> syncFromNotion(String kbId, Map<String, String> config) {
        String token = config.get("integrationToken");
        String databaseId = config.get("databaseId");

        Map<String, Object> result = new HashMap<>();
        try {
            // Query Notion database
            String queryUrl = "https://api.notion.com/v1/databases/" + databaseId + "/query";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Notion-Version", "2022-06-28");
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>("{}", headers);
            ResponseEntity<String> response = restTemplate.exchange(queryUrl, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode results = root.get("results");

            int docCount = 0;
            for (JsonNode page : results) {
                String title = extractNotionTitle(page);
                String content = page.toString();

                // Create document from Notion page
                KnowledgeDocument doc = KnowledgeDocument.builder()
                        .knowledgeBaseId(kbId)
                        .fileName(title != null ? title : "Untitled")
                        .fileType("json")
                        .contentPreview(content.length() > 500 ? content.substring(0, 500) : content)
                        .fileSize((long) content.length())
                        .vectorStatus("PENDING")
                        .charCount(content.length())
                        .build();

                documentRepository.save(doc);
                docCount++;
            }

            result.put("success", true);
            result.put("documentCount", docCount);
            result.put("message", "Successfully synced " + docCount + " pages from Notion");

            // Update KB config
            Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
            if (kbOpt.isPresent()) {
                KnowledgeBase kb = kbOpt.get();
                kb.setConfiguration(objectMapper.writeValueAsString(config));
                knowledgeBaseRepository.save(kb);
            }

        } catch (Exception e) {
            log.error("Failed to sync from Notion", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> testNotionConnection(Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            String token = config.get("integrationToken");
            String url = "https://api.notion.com/v1/users/me";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Notion-Version", "2022-06-28");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                result.put("success", true);
                result.put("message", "Connection successful");
            } else {
                result.put("success", false);
                result.put("message", "Connection failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
        }
        return result;
    }

    @Override
    public List<Map<String, String>> listNotionDatabases(Map<String, String> config) {
        List<Map<String, String>> databases = new ArrayList<>();
        try {
            String token = config.get("integrationToken");
            String url = "https://api.notion.com/v1/search";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Notion-Version", "2022-06-28");
            headers.set("Content-Type", "application/json");

            String body = "{\"filter\": {\"property\": \"object\", \"value\": \"database\"}}";
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode results = root.get("results");

            for (JsonNode db : results) {
                Map<String, String> dbInfo = new HashMap<>();
                dbInfo.put("id", db.get("id").asText());
                if (db.has("title") && db.get("title").isArray() && db.get("title").size() > 0) {
                    dbInfo.put("title", db.get("title").get(0).get("plain_text").asText());
                } else {
                    dbInfo.put("title", "Untitled");
                }
                databases.add(dbInfo);
            }
        } catch (Exception e) {
            log.error("Failed to list Notion databases", e);
        }
        return databases;
    }

    private String extractNotionTitle(JsonNode page) {
        if (page.has("properties")) {
            JsonNode properties = page.get("properties");
            for (JsonNode prop : properties) {
                if (prop.has("title")) {
                    JsonNode title = prop.get("title");
                    if (title.isArray() && title.size() > 0) {
                        return title.get(0).get("plain_text").asText();
                    }
                }
            }
        }
        return "Page-" + page.get("id").asText().substring(0, 8);
    }

    // ==================== Web Sync ====================

    @Override
    public Map<String, Object> syncFromWeb(String kbId, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) config.get("urls");
        int maxDepth = config.containsKey("maxDepth") ? (int) config.get("maxDepth") : 2;

        Map<String, Object> result = new HashMap<>();
        int docCount = 0;

        try {
            for (String url : urls) {
                String content = fetchWebPage(url);
                if (content != null && !content.isEmpty()) {
                    KnowledgeDocument doc = KnowledgeDocument.builder()
                            .knowledgeBaseId(kbId)
                            .fileName(url)
                            .fileType("html")
                            .contentPreview(content.length() > 500 ? content.substring(0, 500) : content)
                            .fileSize((long) content.length())
                            .vectorStatus("PENDING")
                            .charCount(content.length())
                            .build();

                    documentRepository.save(doc);
                    docCount++;
                }
            }

            result.put("success", true);
            result.put("documentCount", docCount);
            result.put("message", "Successfully synced " + docCount + " web pages");

            // Update KB config
            Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
            if (kbOpt.isPresent()) {
                KnowledgeBase kb = kbOpt.get();
                kb.setConfiguration(objectMapper.writeValueAsString(config));
                knowledgeBaseRepository.save(kb);
            }

        } catch (Exception e) {
            log.error("Failed to sync from web", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> testWebUrl(String url) {
        Map<String, Object> result = new HashMap<>();
        boolean usedJina = false;

        // Try Jina Reader first if enabled
        String content = null;
        if (jinaReaderEnabled) {
            try {
                content = fetchViaJinaReader(url);
                if (content != null && !content.isEmpty()) {
                    usedJina = true;
                }
            } catch (Exception e) {
                log.warn("Jina Reader failed, trying direct HTTP: {}", e.getMessage());
            }
        }

        // Fallback to direct HTTP if Jina failed
        if (content == null) {
            content = fetchWebPageDirect(url);
        }

        if (content != null && !content.isEmpty()) {
            result.put("success", true);
            result.put("title", extractPageTitle(content, usedJina));
            result.put("contentLength", content.length());
            result.put("usedJinaReader", usedJina);
            result.put("message", usedJina ? "URL accessible via Jina Reader" : "URL accessible via direct HTTP");
        } else {
            result.put("success", false);
            result.put("message", "Failed to fetch URL");
        }

        return result;
    }

    /**
     * Direct HTTP fetch without Jina Reader (for fallback)
     */
    private String fetchWebPageDirect(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", getRandomUserAgent());

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return stripHtmlTags(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch web page directly: {}", url, e);
        }
        return null;
    }

    private String fetchWebPage(String url) {
        // Try Jina Reader first if enabled
        if (jinaReaderEnabled) {
            try {
                String content = fetchViaJinaReader(url);
                if (content != null && !content.isEmpty()) {
                    log.info("Successfully fetched {} via Jina Reader", url);
                    return content;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch via Jina Reader, falling back to direct HTTP: {}", url, e);
            }
        }

        // Fallback to direct HTTP fetch
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", getRandomUserAgent());

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String html = response.getBody();
                return stripHtmlTags(html);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch web page: {}", url, e);
        }
        return null;
    }

    /**
     * Fetch URL content via Jina Reader API
     * Returns clean Markdown format
     */
    private String fetchViaJinaReader(String url) {
        String jinaApiUrl = "https://r.jina.ai/" + url;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "text/plain");

        if (jinaReaderApiKey != null && !jinaReaderApiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + jinaReaderApiKey);
        }

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(jinaApiUrl, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        }
        return null;
    }

    private String stripHtmlTags(String html) {
        if (html == null) return "";
        // Simple HTML stripping - remove script and style tags first
        html = html.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
        html = html.replaceAll("<style[^>]*>[\\s\\S]*?</style>", "");
        // Remove all HTML tags
        html = html.replaceAll("<[^>]+>", "\n");
        // Clean up whitespace
        html = html.replaceAll("[ \t]+", " ");
        html = html.replaceAll("\n+", "\n");
        return html.trim();
    }

    /**
     * Extract page title from content (supports both HTML and Markdown)
     */
    private String extractPageTitle(String content, boolean isMarkdown) {
        if (content == null || content.isEmpty()) {
            return "Web Page";
        }

        if (isMarkdown) {
            // For Markdown, look for first # heading
            String[] lines = content.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    return trimmed.substring(2).trim();
                }
            }
        } else {
            // For HTML, look for <title> tag
            int start = content.indexOf("<title>");
            int end = content.indexOf("</title>");
            if (start >= 0 && end > start) {
                return content.substring(start + 7, end).trim();
            }
        }

        return "Web Page";
    }

    private String getRandomUserAgent() {
        String[] userAgents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
        };
        return userAgents[(int) (Math.random() * userAgents.length)];
    }

    // ==================== Database Sync ====================

    @Override
    public Map<String, Object> connectDatabase(String kbId, Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();
        String url = config.get("connectionUrl");
        String username = config.get("username");
        String password = config.get("password");

        try {
            Connection conn = DriverManager.getConnection(url, username, password);
            dbConnections.put(kbId, conn);
            dbConfigs.put(kbId, config);

            result.put("success", true);
            result.put("message", "Database connected successfully");
            result.put("schema", getDatabaseSchema(kbId));

            // Update KB config
            Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
            if (kbOpt.isPresent()) {
                KnowledgeBase kb = kbOpt.get();
                // Store masked password
                Map<String, String> maskedConfig = new HashMap<>(config);
                maskedConfig.put("password", "***");
                kb.setConfiguration(objectMapper.writeValueAsString(maskedConfig));
                kb.setType(KnowledgeType.STRUCTURED);
                knowledgeBaseRepository.save(kb);
            }

        } catch (Exception e) {
            log.error("Failed to connect to database", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> testDatabaseConnection(Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();
        String url = config.get("connectionUrl");
        String username = config.get("username");
        String password = config.get("password");

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            result.put("success", true);
            result.put("message", "Connection successful");
            result.put("databaseProductName", conn.getMetaData().getDatabaseProductName());
            result.put("databaseProductVersion", conn.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getDatabaseSchema(String kbId) {
        List<Map<String, Object>> schema = new ArrayList<>();
        Connection conn = dbConnections.get(kbId);

        if (conn == null) {
            // Try to reconnect from config
            Map<String, String> config = dbConfigs.get(kbId);
            if (config != null) {
                try {
                    conn = DriverManager.getConnection(
                        config.get("connectionUrl"),
                        config.get("username"),
                        config.get("password")
                    );
                    dbConnections.put(kbId, conn);
                } catch (SQLException e) {
                    log.error("Failed to reconnect to database", e);
                    return schema;
                }
            } else {
                return schema;
            }
        }

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            String[] types = {"TABLE", "VIEW"};
            ResultSet tables = metaData.getTables(null, null, "%", types);

            while (tables.next()) {
                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("name", tables.getString("TABLE_NAME"));
                tableInfo.put("type", tables.getString("TABLE_TYPE"));

                // Get columns
                List<Map<String, String>> columns = new ArrayList<>();
                ResultSet columnsRs = metaData.getColumns(null, null, tables.getString("TABLE_NAME"), "%");
                while (columnsRs.next()) {
                    Map<String, String> column = new HashMap<>();
                    column.put("name", columnsRs.getString("COLUMN_NAME"));
                    column.put("type", columnsRs.getString("TYPE_NAME"));
                    column.put("nullable", columnsRs.getString("IS_NULLABLE"));
                    columns.add(column);
                }
                tableInfo.put("columns", columns);
                schema.add(tableInfo);
            }
        } catch (SQLException e) {
            log.error("Failed to get database schema", e);
        }

        return schema;
    }

    @Override
    public Map<String, Object> syncDatabaseTable(String kbId, String tableName) {
        Map<String, Object> result = new HashMap<>();
        Connection conn = dbConnections.get(kbId);

        if (conn == null) {
            result.put("success", false);
            result.put("error", "Database not connected");
            return result;
        }

        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " LIMIT 1000");

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            int rowCount = 0;
            StringBuilder content = new StringBuilder();

            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    row.append(meta.getColumnName(i)).append(": ").append(rs.getString(i)).append(" | ");
                }
                content.append(row.toString()).append("\n");
                rowCount++;
            }

            KnowledgeDocument doc = KnowledgeDocument.builder()
                    .knowledgeBaseId(kbId)
                    .fileName(tableName)
                    .fileType("sql")
                    .contentPreview(content.length() > 500 ? content.substring(0, 500) : content.toString())
                    .fileSize((long) content.length())
                    .vectorStatus("PENDING")
                    .charCount(content.length())
                    .build();

            documentRepository.save(doc);

            result.put("success", true);
            result.put("documentCount", 1);
            result.put("rowCount", rowCount);
            result.put("message", "Successfully synced table " + tableName + " with " + rowCount + " rows");

        } catch (Exception e) {
            log.error("Failed to sync database table", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    // ==================== RAGFlow Sync ====================

    @Override
    public Map<String, Object> testRAGFlowConnection(Map<String, String> config) {
        String endpointUrl = config.get("endpointUrl");
        String apiKey = config.get("apiKey");

        Map<String, Object> result = new HashMap<>();
        try {
            boolean connected = ragflowIntegrationService.testConnection(endpointUrl, apiKey);
            result.put("success", connected);
            result.put("message", connected ? "Connection successful" : "Connection failed");
        } catch (Exception e) {
            log.error("RAGFlow connection test failed", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> listRAGFlowKnowledgeBases(Map<String, String> config) {
        String endpointUrl = config.get("endpointUrl");
        String apiKey = config.get("apiKey");

        return ragflowIntegrationService.listKnowledgeBases(endpointUrl, apiKey);
    }

    @Override
    public Map<String, Object> syncFromRAGFlow(String kbId, Map<String, String> config) {
        String endpointUrl = config.get("endpointUrl");
        String apiKey = config.get("apiKey");
        String ragflowKbId = config.get("ragflowKbId");

        Map<String, Object> result = new HashMap<>();
        try {
            // Get knowledge base details from RAGFlow
            Map<String, Object> kbDetails = ragflowIntegrationService.getKnowledgeBase(endpointUrl, apiKey, ragflowKbId);

            // List documents from RAGFlow
            List<Map<String, Object>> documents = ragflowIntegrationService.listDocuments(endpointUrl, apiKey, ragflowKbId);

            // Update ORIN knowledge base configuration
            Optional<KnowledgeBase> kbOpt = knowledgeBaseRepository.findById(kbId);
            if (kbOpt.isPresent()) {
                KnowledgeBase kb = kbOpt.get();
                kb.setType(KnowledgeType.RAGFLOW);
                // Store RAGFlow config in configuration field
                Map<String, Object> kbConfig = new HashMap<>();
                kbConfig.put("endpointUrl", endpointUrl);
                kbConfig.put("apiKey", apiKey);
                kbConfig.put("ragflowKbId", ragflowKbId);
                kb.setConfiguration(objectMapper.writeValueAsString(kbConfig));
                knowledgeBaseRepository.save(kb);
            }

            result.put("success", true);
            result.put("message", "Successfully synced from RAGFlow");
            result.put("documentCount", documents.size());
            result.put("knowledgeBaseDetails", kbDetails);

        } catch (Exception e) {
            log.error("Failed to sync from RAGFlow", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> uploadToRAGFlow(String kbId, Map<String, String> config, String fileName, byte[] fileContent) {
        String endpointUrl = config.get("endpointUrl");
        String apiKey = config.get("apiKey");
        String ragflowKbId = config.get("ragflowKbId");

        return ragflowIntegrationService.uploadDocument(endpointUrl, apiKey, ragflowKbId, fileName, fileContent);
    }

    @Override
    public List<Map<String, Object>> retrievalFromRAGFlow(Map<String, String> config, String kbId, String query, int topK) {
        String endpointUrl = config.get("endpointUrl");
        String apiKey = config.get("apiKey");

        return ragflowIntegrationService.retrievalTest(endpointUrl, apiKey, kbId, query, topK);
    }
}
