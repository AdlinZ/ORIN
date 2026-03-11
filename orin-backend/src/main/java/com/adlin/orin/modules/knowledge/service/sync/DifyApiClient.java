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
            String url = buildUrl(endpointUrl, "/v1/datasets?page=1&limit=100");
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null) {
                    for (Map<String, Object> item : data) {
                        datasets.add(parseDataset(item));
                    }
                }
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
            String url = buildUrl(endpointUrl, String.format("/v1/datasets/%s/documents?page=1&limit=100", datasetId));
            HttpHeaders headers = createHeaders(apiKey);
            
            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null) {
                    for (Map<String, Object> item : data) {
                        documents.add(parseDocument(item));
                    }
                }
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
        
        // 确保 path 以 v1 开头
        if (!path.startsWith("v1/")) {
            path = "v1/" + path;
        }
        
        return base + path;
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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
