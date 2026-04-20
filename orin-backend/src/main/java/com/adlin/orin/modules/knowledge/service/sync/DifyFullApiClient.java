package com.adlin.orin.modules.knowledge.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dify 完整 API 客户端
 * 支持知识库、文档、应用、工作流、用户等同步
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DifyFullApiClient {

    private final RestTemplate difyRestTemplate;

    @Value("${dify.api.timeout:60000}")
    private int apiTimeout;

    private static final DateTimeFormatter DIFY_DATE_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    private HttpHeaders createHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    private String buildUrl(String endpointUrl, String path) {
        String base = (endpointUrl == null || endpointUrl.isBlank()) ? "http://localhost:3000/v1" : endpointUrl.trim();
        if (!base.startsWith("http")) {
            base = "http://" + base;
        }
        if (base.endsWith("/v1") || base.endsWith("/v1/")) {
            base = base.replaceAll("/v1/?$", "");
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedPath.isEmpty()) {
            normalizedPath = "/v1";
        } else if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        if (!normalizedPath.startsWith("/v1/") && !"/v1".equals(normalizedPath)) {
            normalizedPath = "/v1" + normalizedPath;
        }
        return base + normalizedPath;
    }

    // ==================== 连接测试 ====================

    public boolean testConnection(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/v1/datasets");
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Dify connection test failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 工作区信息 ====================

    /**
     * 获取当前用户信息
     */
    public Map<String, Object> getCurrentUser(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/v1/users/me");
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get current user: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取工作区信息
     */
    public Map<String, Object> getWorkspaceInfo(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/v1/workspaces");
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get workspace info: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 应用 (Apps) ====================

    /**
     * 获取应用列表
     */
    public List<Map<String, Object>> listApps(String endpointUrl, String apiKey) {
        List<Map<String, Object>> apps = new ArrayList<>();
        
        try {
            HttpHeaders headers = createHeaders(apiKey);
            int page = 1;
            final int limit = 100;

            while (true) {
                String url = buildUrl(endpointUrl, String.format("/v1/apps?page=%d&limit=%d", page, limit));
                ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {});

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null || data.isEmpty()) {
                    break;
                }
                apps.addAll(data);
                if (data.size() < limit) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Failed to list apps from Dify: {}", e.getMessage());
        }
        
        return apps;
    }

    /**
     * 获取应用详情
     */
    public Map<String, Object> getAppInfo(String endpointUrl, String apiKey, String appId) {
        try {
            String url = buildUrl(endpointUrl, "/v1/apps/" + appId);
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get app info: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取应用配置 (含提示词等)
     */
    public Map<String, Object> getAppConfig(String endpointUrl, String apiKey, String appId) {
        try {
            String url = buildUrl(endpointUrl, "/v1/apps/" + appId + "/model-config");
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get app config: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 工作流 (Workflows) ====================

    /**
     * 获取工作流列表
     */
    public List<Map<String, Object>> listWorkflows(String endpointUrl, String apiKey) {
        List<Map<String, Object>> workflows = new ArrayList<>();
        
        try {
            HttpHeaders headers = createHeaders(apiKey);
            int page = 1;
            final int limit = 100;

            while (true) {
                String url = buildUrl(endpointUrl,
                        String.format("/v1/apps?page=%d&limit=%d&type=workflow", page, limit));
                ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {});

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null || data.isEmpty()) {
                    break;
                }
                workflows.addAll(data);
                if (data.size() < limit) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Failed to list workflows from Dify: {}", e.getMessage());
        }
        
        return workflows;
    }

    /**
     * 获取工作流DSL
     */
    public Map<String, Object> getWorkflowDSL(String endpointUrl, String apiKey, String appId) {
        try {
            String url = buildUrl(endpointUrl, "/v1/apps/" + appId + "/workflows/export");
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get workflow DSL: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 知识库 (Datasets) ====================

    /**
     * 获取知识库列表
     */
    public List<DifyKnowledgeSyncService.DifyDataset> listDatasets(String endpointUrl, String apiKey) {
        List<DifyKnowledgeSyncService.DifyDataset> datasets = new ArrayList<>();
        
        try {
            HttpHeaders headers = createHeaders(apiKey);
            int page = 1;
            final int limit = 100;

            while (true) {
                String url = buildUrl(endpointUrl, String.format("/v1/datasets?page=%d&limit=%d", page, limit));
                ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {});

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null || data.isEmpty()) {
                    break;
                }
                for (Map<String, Object> item : data) {
                    datasets.add(parseDataset(item));
                }
                if (data.size() < limit) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Failed to list datasets from Dify: {}", e.getMessage());
        }
        
        return datasets;
    }

    /**
     * 获取知识库详情
     */
    public Map<String, Object> getDatasetInfo(String endpointUrl, String apiKey, String datasetId) {
        try {
            String url = buildUrl(endpointUrl, "/v1/datasets/" + datasetId);
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get dataset info: {}", e.getMessage());
        }
        return null;
    }

    private DifyKnowledgeSyncService.DifyDataset parseDataset(Map<String, Object> item) {
        DifyKnowledgeSyncService.DifyDataset dataset = new DifyKnowledgeSyncService.DifyDataset();
        dataset.setId(String.valueOf(item.get("id")));
        dataset.setName(String.valueOf(item.get("name")));
        dataset.setDescription(String.valueOf(item.get("description")));
        dataset.setDocumentCount(item.get("document_count") != null ? 
                ((Number) item.get("document_count")).intValue() : 0);
        
        String createdAt = String.valueOf(item.get("created_at"));
        if (createdAt != null && !createdAt.equals("null")) {
            try {
                long timestamp = Long.parseLong(createdAt);
                dataset.setCreatedAt(LocalDateTime.ofEpochSecond(timestamp, 0, java.time.ZoneOffset.UTC));
            } catch (Exception ignored) {}
        }
        
        return dataset;
    }

    // ==================== 文档 (Documents) ====================

    /**
     * 获取知识库下的文档列表
     */
    public List<DifyKnowledgeSyncService.DifyDocument> listDocuments(String endpointUrl, String apiKey, String datasetId) {
        List<DifyKnowledgeSyncService.DifyDocument> documents = new ArrayList<>();
        
        try {
            HttpHeaders headers = createHeaders(apiKey);
            int page = 1;
            final int limit = 100;

            while (true) {
                String url = buildUrl(endpointUrl,
                        String.format("/v1/datasets/%s/documents?page=%d&limit=%d", datasetId, page, limit));
                ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {});

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null || data.isEmpty()) {
                    break;
                }
                for (Map<String, Object> item : data) {
                    documents.add(parseDocument(item));
                }
                if (data.size() < limit) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Failed to list documents from Dify: {}", e.getMessage());
        }
        
        return documents;
    }

    /**
     * 获取文档内容
     */
    public String getDocumentContent(String endpointUrl, String apiKey, String datasetId, String documentId) {
        try {
            String url = buildUrl(endpointUrl, String.format("/v1/datasets/%s/documents/%s/content", datasetId, documentId));
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return String.valueOf(response.getBody().get("content"));
            }
        } catch (Exception e) {
            log.error("Failed to get document content: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取文档详情
     */
    public Map<String, Object> getDocument(String endpointUrl, String apiKey, String datasetId, String documentId) {
        try {
            String url = buildUrl(endpointUrl, String.format("/v1/datasets/%s/documents/%s", datasetId, documentId));
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get document: {}", e.getMessage());
        }
        return null;
    }

    private DifyKnowledgeSyncService.DifyDocument parseDocument(Map<String, Object> item) {
        DifyKnowledgeSyncService.DifyDocument doc = new DifyKnowledgeSyncService.DifyDocument();
        doc.setId(String.valueOf(item.get("id")));
        doc.setName(String.valueOf(item.get("name")));
        doc.setDocumentId(String.valueOf(item.get("id")));
        doc.setKnowledgeBaseId(String.valueOf(item.get("dataset_id")));
        
        String status = String.valueOf(item.get("status"));
        doc.setStatus(status);
        
        String wordCount = String.valueOf(item.get("word_count"));
        if (wordCount != null && !wordCount.equals("null")) {
            try {
                doc.setWordCount(Integer.parseInt(wordCount));
            } catch (Exception ignored) {}
        }
        
        return doc;
    }

    // ==================== 对话 (Conversations) ====================

    /**
     * 获取对话历史列表
     */
    public List<Map<String, Object>> listConversations(String endpointUrl, String apiKey, String appId) {
        List<Map<String, Object>> conversations = new ArrayList<>();
        
        try {
            HttpHeaders headers = createHeaders(apiKey);
            int page = 1;
            final int limit = 50;

            while (true) {
                String url = buildUrl(endpointUrl,
                        String.format("/v1/apps/%s/conversations?page=%d&limit=%d", appId, page, limit));
                ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {});

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null || data.isEmpty()) {
                    break;
                }
                conversations.addAll(data);
                if (data.size() < limit) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Failed to list conversations: {}", e.getMessage());
        }
        
        return conversations;
    }

    /**
     * 获取单条对话详情
     */
    public Map<String, Object> getConversation(String endpointUrl, String apiKey, String appId, String conversationId) {
        try {
            String url = buildUrl(endpointUrl, String.format("/v1/apps/%s/conversations/%s", appId, conversationId));
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get conversation: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取对话中的消息列表
     */
    public List<Map<String, Object>> listMessages(String endpointUrl, String apiKey, String appId, String conversationId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        try {
            String url = buildUrl(endpointUrl, String.format("/v1/apps/%s/conversations/%s/messages?page=1&limit=50", 
                    appId, conversationId));
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null) {
                    messages.addAll(data);
                }
            }
        } catch (Exception e) {
            log.error("Failed to list messages: {}", e.getMessage());
        }
        
        return messages;
    }

    // ==================== API Keys ====================

    /**
     * 获取 API Keys 列表
     */
    public List<Map<String, Object>> listApiKeys(String endpointUrl, String apiKey) {
        List<Map<String, Object>> apiKeys = new ArrayList<>();
        
        try {
            String url = buildUrl(endpointUrl, "/v1/api-keys");
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null) {
                    apiKeys.addAll(data);
                }
            }
        } catch (Exception e) {
            log.error("Failed to list API keys: {}", e.getMessage());
        }
        
        return apiKeys;
    }

    // ==================== RAG 检索 ====================

    public List<Map<String, Object>> retrieveFromDataset(String endpointUrl, String apiKey, 
            String datasetId, String query, int topK) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            String url = buildUrl(endpointUrl, String.format("/v1/datasets/%s/retrieval", datasetId));
            HttpHeaders headers = createHeaders(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("retrieval_strategy", "semantic");
            body.put("top_k", topK);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null) {
                    results.addAll(data);
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve from dataset: {}", e.getMessage());
        }
        
        return results;
    }

    // ==================== 统计信息 ====================

    /**
     * 获取应用统计信息
     */
    public Map<String, Object> getAppStatistics(String endpointUrl, String apiKey, String appId) {
        try {
            String url = buildUrl(endpointUrl, String.format("/v1/apps/%s/statistics", appId));
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get app statistics: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 全文 ====================

    /**
     * 完整同步 Dify 数据
     * 返回所有可同步的内容
     */
    public Map<String, Object> fullSync(String endpointUrl, String apiKey) {
        Map<String, Object> result = new HashMap<>();
        
        // 用户信息
        result.put("user", getCurrentUser(endpointUrl, apiKey));
        
        // 工作区信息
        result.put("workspace", getWorkspaceInfo(endpointUrl, apiKey));
        
        // 应用列表
        result.put("apps", listApps(endpointUrl, apiKey));
        
        // 工作流列表
        result.put("workflows", listWorkflows(endpointUrl, apiKey));
        
        // 知识库列表
        result.put("datasets", listDatasets(endpointUrl, apiKey));
        
        // API Keys
        result.put("apiKeys", listApiKeys(endpointUrl, apiKey));
        
        log.info("Full sync completed for endpoint: {}", endpointUrl);
        
        return result;
    }
}
