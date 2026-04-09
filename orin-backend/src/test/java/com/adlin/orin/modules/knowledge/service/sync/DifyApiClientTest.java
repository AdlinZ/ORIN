package com.adlin.orin.modules.knowledge.service.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DifyApiClient 单元测试
 * 测试分页、异常、鉴权失败等场景
 */
@ExtendWith(MockitoExtension.class)
class DifyApiClientTest {

    @Mock
    private RestTemplate difyRestTemplate;

    private DifyApiClient client() {
        return new DifyApiClient(difyRestTemplate);
    }

    // ==================== testConnection ====================

    @Test
    @DisplayName("testConnection: 返回 200 时成功")
    void testConnection_Success() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("code", 0)));

        boolean result = client().testConnection("http://localhost:3000", "test-key");

        assertTrue(result);
    }

    @Test
    @DisplayName("testConnection: 返回非 2xx 时失败")
    void testConnection_Not2xx() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.status(401).build());

        boolean result = client().testConnection("http://localhost:3000", "bad-key");

        assertFalse(result);
    }

    @Test
    @DisplayName("testConnection: 网络异常时返回 false")
    void testConnection_Exception() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        boolean result = client().testConnection("http://localhost:3000", "test-key");

        assertFalse(result);
    }

    // ==================== listDatasets ====================

    @Test
    @DisplayName("listDatasets: 正常返回数据集列表")
    void listDatasets_Success() {
        Map<String, Object> responseBody = Map.of(
                "data", List.of(
                        Map.of("id", "ds-1", "name", "KB1", "document_count", 10, "created_at", 1700000000),
                        Map.of("id", "ds-2", "name", "KB2", "document_count", 5, "created_at", 1700000001)
                )
        );
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        var datasets = client().listDatasets("http://localhost:3000", "test-key");

        assertEquals(2, datasets.size());
        assertEquals("ds-1", datasets.get(0).getId());
        assertEquals("KB1", datasets.get(0).getName());
        assertEquals(10, datasets.get(0).getDocumentCount());
    }

    @Test
    @DisplayName("listDatasets: 空数据返回空列表")
    void listDatasets_Empty() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of())));

        var datasets = client().listDatasets("http://localhost:3000", "test-key");

        assertTrue(datasets.isEmpty());
    }

    @Test
    @DisplayName("listDatasets: 鉴权失败返回空")
    void listDatasets_AuthFailure() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        HttpHeaders.EMPTY, new byte[0], null));

        var datasets = client().listDatasets("http://localhost:3000", "invalid-key");

        assertTrue(datasets.isEmpty());
    }

    @Test
    @DisplayName("listDatasets: 服务器 500 不抛异常，返回空")
    void listDatasets_ServerError() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error",
                        HttpHeaders.EMPTY, new byte[0], null));

        var datasets = client().listDatasets("http://localhost:3000", "test-key");

        assertTrue(datasets.isEmpty());
    }

    // ==================== listDocuments ====================

    @Test
    @DisplayName("listDocuments: 返回文档列表")
    void listDocuments_Success() {
        Map<String, Object> responseBody = Map.of(
                "data", List.of(
                        Map.of("id", "doc-1", "name", "doc1.pdf", "type", "document", "created_at", 1700000000)
                )
        );
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        var docs = client().listDocuments("http://localhost:3000", "test-key", "ds-1");

        assertEquals(1, docs.size());
        assertEquals("doc-1", docs.get(0).getId());
    }

    @Test
    @DisplayName("listDocuments: 异常返回空列表")
    void listDocuments_Exception() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Network error"));

        var docs = client().listDocuments("http://localhost:3000", "test-key", "ds-1");

        assertTrue(docs.isEmpty());
    }

    // ==================== retrieveFromDataset ====================

    @Test
    @DisplayName("retrieveFromDataset: 返回检索结果")
    void retrieveFromDataset_Success() {
        Map<String, Object> responseBody = Map.of(
                "records", List.of(
                        Map.of("content", "ORIN 是一个 AI 平台", "score", 0.95, "document_id", "doc-1"),
                        Map.of("content", "ORIN 提供知识库功能", "score", 0.88, "document_id", "doc-2")
                )
        );
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        List<Map<String, Object>> results = client().retrieveFromDataset(
                "http://localhost:3000", "test-key", "ds-1", "ORIN 是什么", 5);

        assertEquals(2, results.size());
        assertEquals("ORIN 是一个 AI 平台", results.get(0).get("content"));
        assertEquals(0.95, results.get(0).get("score"));
    }

    @Test
    @DisplayName("retrieveFromDataset: 异常时返回空列表")
    void retrieveFromDataset_Exception() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Network error"));

        List<Map<String, Object>> results = client().retrieveFromDataset(
                "http://localhost:3000", "test-key", "ds-1", "query", 5);

        assertTrue(results.isEmpty());
    }

    // ==================== deleteDocument ====================

    @Test
    @DisplayName("deleteDocument: 204 删除成功")
    void deleteDocument_Success_204() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.noContent().build());

        boolean result = client().deleteDocument("http://localhost:3000", "test-key", "ds-1", "doc-1");

        assertTrue(result);
    }

    @Test
    @DisplayName("deleteDocument: 200 也算成功")
    void deleteDocument_Success_200() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok().build());

        boolean result = client().deleteDocument("http://localhost:3000", "test-key", "ds-1", "doc-1");

        assertTrue(result);
    }

    @Test
    @DisplayName("deleteDocument: 异常返回 false")
    void deleteDocument_Exception() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Connection reset"));

        boolean result = client().deleteDocument("http://localhost:3000", "test-key", "ds-1", "doc-1");

        assertFalse(result);
    }

    // ==================== getDocumentContent ====================

    @Test
    @DisplayName("getDocumentContent: 返回内容文本")
    void getDocumentContent_Success() {
        Map<String, Object> responseBody = Map.of(
                "content", "这是文档内容，包含中文文本。"
        );
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        String content = client().getDocumentContent("http://localhost:3000", "test-key", "ds-1", "doc-1");

        assertEquals("这是文档内容，包含中文文本。", content);
    }

    @Test
    @DisplayName("getDocumentContent: 异常时返回空字符串")
    void getDocumentContent_Exception() {
        when(difyRestTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Not found"));

        String content = client().getDocumentContent("http://localhost:3000", "test-key", "ds-1", "doc-1");

        assertEquals("", content);
    }
}
