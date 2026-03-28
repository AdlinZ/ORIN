package com.adlin.orin.modules.knowledge.service.impl;

import com.adlin.orin.modules.knowledge.service.RAGFlowIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGFlowIntegrationServiceImpl implements RAGFlowIntegrationService {

    private final RestTemplate ragflowRestTemplate;

    @Value("${ragflow.default.endpoint:http://localhost:9380/v1}")
    private String defaultEndpoint;

    private String buildUrl(String endpointUrl, String path) {
        String baseUrl = endpointUrl != null && !endpointUrl.isEmpty() ? endpointUrl : defaultEndpoint;
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + path;
    }

    private HttpHeaders createHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Override
    public boolean testConnection(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/knowledge_base/list");

            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("RAGFlow connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> createKnowledgeBase(String endpointUrl, String apiKey, String name, String description) {
        try {
            String url = buildUrl(endpointUrl, "/knowledge_base/create");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            if (description != null) {
                body.put("description", description);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return Map.of("success", false, "message", "Failed to create knowledge base");
        } catch (Exception e) {
            log.error("Failed to create RAGFlow knowledge base: {}", e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listKnowledgeBases(String endpointUrl, String apiKey) {
        try {
            String url = buildUrl(endpointUrl, "/knowledge_base/list");

            HttpHeaders headers = createHeaders(apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object data = body.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to list RAGFlow knowledge bases: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getKnowledgeBase(String endpointUrl, String apiKey, String kbId) {
        try {
            String url = buildUrl(endpointUrl, "/knowledge_base/detail");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("kb_id", kbId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return Map.of("success", false, "message", "Knowledge base not found");
        } catch (Exception e) {
            log.error("Failed to get RAGFlow knowledge base: {}", e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> deleteKnowledgeBase(String endpointUrl, String apiKey, String kbId) {
        try {
            String url = buildUrl(endpointUrl, "/knowledge_base/rm");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("kb_id", kbId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return Map.of("success", false, "message", "Failed to delete knowledge base");
        } catch (Exception e) {
            log.error("Failed to delete RAGFlow knowledge base: {}", e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> uploadDocument(String endpointUrl, String apiKey, String kbId,
                                               String fileName, byte[] fileContent) {
        try {
            String url = buildUrl(endpointUrl, "/document/upload");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("kb_id", kbId);

            // 添加文件
            ByteArrayResource resource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return Map.of("success", false, "message", "Failed to upload document");
        } catch (Exception e) {
            log.error("Failed to upload document to RAGFlow: {}", e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listDocuments(String endpointUrl, String apiKey, String kbId) {
        try {
            String url = buildUrl(endpointUrl, "/document/list");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("kb_id", kbId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                Object data = result.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to list RAGFlow documents: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> deleteDocument(String endpointUrl, String apiKey, String docId) {
        try {
            String url = buildUrl(endpointUrl, "/document/rm");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("doc_id", docId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return Map.of("success", false, "message", "Failed to delete document");
        } catch (Exception e) {
            log.error("Failed to delete RAGFlow document: {}", e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> retrievalTest(String endpointUrl, String apiKey, String kbId,
                                                     String query, int topK) {
        try {
            String url = buildUrl(endpointUrl, "/chunk/retrieval_test");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("kb_id", kbId);
            body.put("question", query);
            body.put("top_k", topK);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                Object data = result.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to retrieval test from RAGFlow: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public String downloadDocument(String endpointUrl, String apiKey, String docId) {
        try {
            String url = buildUrl(endpointUrl, "/document/download");

            HttpHeaders headers = createHeaders(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("doc_id", docId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = ragflowRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null) {
                    // RAGFlow returns raw text under "text" or "$pdf2txt" key
                    Object text = data.get("text");
                    if (text != null) {
                        return text.toString();
                    }
                    Object pdfText = data.get("$pdf2txt");
                    if (pdfText != null) {
                        return pdfText.toString();
                    }
                }
            }
            return "";
        } catch (Exception e) {
            log.error("Failed to download document from RAGFlow: {}", e.getMessage(), e);
            return "";
        }
    }
}
