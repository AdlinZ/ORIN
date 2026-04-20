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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dify API 客户端
 * 封装知识库相关的 API 调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DifyApiClient {

    private final RestTemplate difyRestTemplate;

    @Value("${dify.api.timeout:60000}")
    private int apiTimeout;

    private static final DateTimeFormatter DIFY_DATE_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * 测试 Dify 连接
     */
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

    /**
     * 获取知识库列表
     */
    public List<DifyKnowledgeSyncService.DifyDataset> listDatasets(String endpointUrl, String apiKey) {
        List<DifyKnowledgeSyncService.DifyDataset> datasets = new ArrayList<>();
        
        try {
            int page = 1;
            final int limit = 100;
            HttpHeaders headers = createHeaders(apiKey);

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
            log.error("Failed to list documents from Dify dataset {}: {}", datasetId, e.getMessage());
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
                // Dify 返回的文档内容格式可能不同，尝试获取
                Object content = response.getBody().get("content");
                if (content != null) {
                    return content.toString();
                }
                // 可能是分段列表
                List<Map<String, Object>> segments = (List<Map<String, Object>>) response.getBody().get("segments");
                if (segments != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Map<String, Object> seg : segments) {
                        Object segContent = seg.get("content");
                        if (segContent != null) {
                            sb.append(segContent.toString()).append("\n");
                        }
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get document content from Dify: {}", e.getMessage());
        }
        
        return "";
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

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get document from Dify: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 创建文档（文本直接写入）
     * POST /v1/datasets/{dataset_id}/documents
     */
    @SuppressWarnings("unchecked")
    public String createDocument(String endpointUrl, String apiKey, String datasetId,
                                 String docName, String content) {
        try {
            String url = buildUrl(endpointUrl, String.format("/v1/datasets/%s/documents", datasetId));
            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("indexing_technique", "high_quality");
            body.put("process_rule", Map.of(
                    "mode", "custom",
                    "rules", Map.of(
                            "pre_processing_rules", List.of(
                                    Map.of("id", "remove_extra_spaces", "enabled", true),
                                    Map.of("id", "remove_urls_and_emails", "enabled", false)
                            ),
                            "segmentation", Map.of("separator", "\n", "max_tokens", 500)
                    )
            ));

            Map<String, Object> docForm = new java.util.HashMap<>();
            docForm.put("data_source", Map.of(
                    "type", "upload_file",
                    "file_info", Map.of("file_id", "")
            ));
            docForm.put("indexing_technique", "high_quality");
            docForm.put("process_rule", body.get("process_rule"));

            // Dify v1 API: upload via separated API, then create doc reference
            // For simplicity, use "upload_file" then create doc with text_input
            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("indexing_technique", "high_quality");
            requestBody.put("process_rule", body.get("process_rule"));
            requestBody.put("doc_form", "text");
            requestBody.put("doc_language", "Auto");
            requestBody.put("name", docName);

            // If content is provided, use text input mode
            if (content != null && !content.isEmpty()) {
                requestBody.put("data_source", Map.of(
                        "type", "upload_file",
                        "file_info", Map.of("file_name", docName + ".txt")
                ));
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                // Dify returns { "data": { "document": { "id": "xxx" }, "batch": "xxx" } }
                Object data = result.get("data");
                if (data instanceof Map) {
                    Map<String, Object> dataMap = (Map<String, Object>) data;
                    Object doc = dataMap.get("document");
                    if (doc instanceof Map) {
                        return (String) ((Map<String, Object>) doc).get("id");
                    }
                }
                // Fallback: try batch field
                Object batch = result.get("batch");
                if (batch != null) {
                    return batch.toString();
                }
            }
            log.warn("Unexpected create document response: {}, body: {}", response.getStatusCode(), response.getBody());
        } catch (Exception e) {
            log.error("Failed to create document in Dify dataset {}: {}", datasetId, e.getMessage());
        }
        return null;
    }

    /**
     * 上传文档内容（分块上传）
     * POST /v1/datasets/{dataset_id}/documents/{document_id}/upload
     */
    public boolean uploadDocumentContent(String endpointUrl, String apiKey, String datasetId,
                                          String documentId, String content) {
        try {
            String url = buildUrl(endpointUrl,
                    String.format("/v1/datasets/%s/documents/%s/upload", datasetId, documentId));
            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("file", List.of(Map.of(
                    "type", "text",
                    "name", "content.txt",
                    "content", content != null ? content : ""
            )));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful()) {
                return true;
            }
            log.warn("uploadDocumentContent failed with status {}: {}", response.getStatusCode(), response.getBody());
        } catch (Exception e) {
            log.error("Failed to upload document content to Dify {}/{}: {}", datasetId, documentId, e.getMessage());
        }
        return false;
    }

    /**
     * 增量更新文档（重新上传文本内容）
     * DELETE then re-CREATE is Dify's update pattern for dataset docs
     * Returns the new document ID, or null on failure
     */
    public String updateDocument(String endpointUrl, String apiKey, String datasetId,
                                 String oldDocumentId, String docName, String content) {
        // Dify doesn't have a direct update API — delete and recreate
        deleteDocument(endpointUrl, apiKey, datasetId, oldDocumentId);
        String newId = createDocument(endpointUrl, apiKey, datasetId, docName, content);
        if (newId != null) {
            uploadDocumentContent(endpointUrl, apiKey, datasetId, newId, content);
        }
        return newId;
    }

    /**
     * 删除 Dify 数据集中的文档
     * DELETE /v1/datasets/{dataset_id}/documents/{document_id}
     */
    public boolean deleteDocument(String endpointUrl, String apiKey, String datasetId, String documentId) {
        try {
            String url = buildUrl(endpointUrl,
                    String.format("/v1/datasets/%s/documents/%s", datasetId, documentId));
            HttpHeaders headers = createHeaders(apiKey);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.DELETE, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            // 200/204 are both successes for delete
            if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 204) {
                return true;
            }
            log.warn("deleteDocument failed with status {}: {}", response.getStatusCode(), response.getBody());
        } catch (Exception e) {
            log.error("Failed to delete document {} from Dify dataset {}: {}", documentId, datasetId, e.getMessage());
        }
        return false;
    }

    /**
     * 从数据集检索（语义搜索）
     * POST /v1/datasets/{dataset_id}/retrieve
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> retrieveFromDataset(String endpointUrl, String apiKey,
                                                          String datasetId, String query, int topK) {
        try {
            String url = buildUrl(endpointUrl,
                    String.format("/v1/datasets/%s/retrieve", datasetId));
            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("query", query);
            requestBody.put("top_k", topK);
            requestBody.put("rerank_model", Map.of("rerank_model_name", ""));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object records = response.getBody().get("records");
                if (records instanceof List) {
                    return (List<Map<String, Object>>) records;
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve from Dify dataset {}: {}", datasetId, e.getMessage());
        }
        return List.of();
    }

    // ==================== 私有方法 ====================

    private HttpHeaders createHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String buildUrl(String endpointUrl, String path) {
        String base = endpointUrl != null ? endpointUrl : "http://localhost:3000/v1";

        if (!base.startsWith("http")) {
            base = "http://" + base;
        }

        // 移除末尾的 /v1 或 /v1/ 以避免重复
        if (base.endsWith("/v1") || base.endsWith("/v1/")) {
            base = base.replaceAll("/v1/?$", "");
        }

        if (!base.endsWith("/")) {
            base += "/";
        }

        String normalizedPath = path == null ? "" : path.trim();
        if (normalizedPath.isEmpty()) {
            normalizedPath = "/v1";
        } else if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        // 若调用方已传 /v1/* 则不重复前缀
        if (!normalizedPath.startsWith("/v1/") && !"/v1".equals(normalizedPath)) {
            normalizedPath = "/v1" + normalizedPath;
        }

        return base.substring(0, base.length() - 1) + normalizedPath;
    }

    private DifyKnowledgeSyncService.DifyDataset parseDataset(Map<String, Object> item) {
        return DifyKnowledgeSyncService.DifyDataset.builder()
                .id((String) item.get("id"))
                .name((String) item.get("name"))
                .description((String) item.get("description"))
                .documentCount(parseInt(item.get("document_count")))
                .createdAt(parseDateTime(item.get("created_at")))
                .updatedAt(parseDateTime(item.get("updated_at")))
                .build();
    }

    private DifyKnowledgeSyncService.DifyDocument parseDocument(Map<String, Object> item) {
        return DifyKnowledgeSyncService.DifyDocument.builder()
                .id((String) item.get("id"))
                .name((String) item.get("name"))
                .type((String) item.get("type"))
                .createdAt(parseDateTime(item.get("created_at")))
                .updatedAt(parseDateTime(item.get("updated_at")))
                .build();
    }

    private Integer parseInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        
        if (value instanceof Integer) {
            // Unix timestamp
            return LocalDateTime.ofEpochSecond(((Integer) value), 0, java.time.ZoneOffset.UTC);
        }
        
        if (value instanceof String) {
            try {
                return LocalDateTime.parse((String) value, DIFY_DATE_FORMAT);
            } catch (Exception e) {
                // 尝试其他格式
                try {
                    long epoch = Long.parseLong((String) value);
                    return LocalDateTime.ofEpochSecond(epoch, 0, java.time.ZoneOffset.UTC);
                } catch (Exception e2) {
                    return null;
                }
            }
        }
        
        return null;
    }
}
