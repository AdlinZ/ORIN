package com.adlin.orin.modules.audit.controlleur;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AuditLogExportController 单测（Phase 3 起步 · 小刀 9a）。
 *
 * 覆盖纯函数 / 序列化（不起 Spring 上下文）：
 * - escapeCsv (RFC 4180)：含逗号 / 双引号 / 换行的字段正确 escape
 * - AuditLogRow JSON 序列化：所有字段都映射到 JSON
 * - MAX_EXPORT_ROWS 上限：明示 100k 防拖库
 *
 * 不测试私有方法（parseTime / buildFilename / ExportFormat.parse）—— 反射调
 * 私有方法的测试脆弱、易碎、易受 JDK 反射行为变化影响。如需测这些，
 * 应在 controller 公开 API 层面（`/export` 端点集成测试）覆盖。
 */
class AuditLogExportControllerTest {

    // ---- escapeCsv (RFC 4180) ----

    @Test
    void escapeCsv_plain_text_unchanged() {
        assertEquals("hello", AuditLogExportController.escapeCsv("hello"));
        assertEquals("", AuditLogExportController.escapeCsv(""));
        assertEquals("", AuditLogExportController.escapeCsv(null));
    }

    @Test
    void escapeCsv_field_with_comma_quoted() {
        assertEquals("\"a,b\"", AuditLogExportController.escapeCsv("a,b"));
    }

    @Test
    void escapeCsv_field_with_quote_escaped_and_quoted() {
        // RFC 4180: " -> ""
        assertEquals("\"he said \"\"hi\"\"\"", AuditLogExportController.escapeCsv("he said \"hi\""));
    }

    @Test
    void escapeCsv_field_with_newline_quoted() {
        assertEquals("\"line1\nline2\"", AuditLogExportController.escapeCsv("line1\nline2"));
    }

    @Test
    void escapeCsv_field_with_carriage_return_quoted() {
        assertEquals("\"a\rb\"", AuditLogExportController.escapeCsv("a\rb"));
    }

    @Test
    void escapeCsv_field_with_all_specials() {
        String tricky = "a,b\"c\nd";
        String result = AuditLogExportController.escapeCsv(tricky);
        assertEquals("\"a,b\"\"c\nd\"", result);
    }

    // ---- AuditLogRow JSON 序列化 ----

    @Test
    void audit_log_row_json_contains_all_fields() throws Exception {
        // createdAt 留空 —— Jackson 默认不带 jsr310 module，序列化 LocalDateTime 抛错
        // （生产路径会注入 `jackson-datatype-jsr310`，本单测聚焦字段映射）
        AuditLog log = AuditLog.builder()
                .id("log-001")
                .userId("user-1")
                .apiKeyId("ak-1")
                .providerId("openai")
                .providerType("openai")
                .endpoint("/v1/chat/completions")
                .method("POST")
                .model("gpt-4")
                .modelAlias("gpt-4-alias")
                .providerModel("gpt-4-actual")
                .ipAddress("10.0.0.1")
                .userAgent("curl/7.85")
                .requestParams("{\"messages\":[]}")
                .responseContent("{}")
                .statusCode(200)
                .responseTime(123L)
                .promptTokens(10)
                .errorCode("70005")
                .build();

        AuditLogExportController.AuditLogRow row = new AuditLogExportController.AuditLogRow(log);
        String json = new ObjectMapper().writeValueAsString(row);

        // 关键字段都在
        assertTrue(json.contains("\"id\":\"log-001\""), json);
        assertTrue(json.contains("\"userId\":\"user-1\""), json);
        assertTrue(json.contains("\"apiKeyId\":\"ak-1\""), json);
        assertTrue(json.contains("\"endpoint\":\"/v1/chat/completions\""), json);
        assertTrue(json.contains("\"statusCode\":200"), json);
        assertTrue(json.contains("\"promptTokens\":10"), json);
        assertTrue(json.contains("\"createdAt\":null"), json);
        // Gateway MVP: 三新字段 (V93)
        assertTrue(json.contains("\"modelAlias\":\"gpt-4-alias\""), json);
        assertTrue(json.contains("\"providerModel\":\"gpt-4-actual\""), json);
        assertTrue(json.contains("\"errorCode\":\"70005\""), json);
    }

    @Test
    void audit_log_row_csv_header_includes_gateway_fields() throws Exception {
        // 验证 CSV 导出表头含 modelAlias / providerModel / errorCode
        AuditLogRepository repository = mock(AuditLogRepository.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuditLogExportController controller =
                new AuditLogExportController(repository, new ObjectMapper());
        AuditLog log = AuditLog.builder()
                .id("log-csv")
                .userId("user-1")
                .endpoint("/one")
                .modelAlias("gpt-4-alias")
                .providerModel("gpt-4-actual")
                .errorCode("100003")
                .build();
        when(repository.findForExport(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq((String) null),
                eq((String) null),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 1_000), 1));

        StreamingResponseBody body = controller.export("csv", null, null, null, null, response);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        body.writeTo(output);

        String csv = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        // 表头应含 3 个新列；顺序在 model 之后、createdAt 之前
        String header = csv.split("\r\n", 2)[0];
        assertTrue(header.contains("modelAlias"), header);
        assertTrue(header.contains("providerModel"), header);
        assertTrue(header.contains("errorCode"), header);
        // 数据行也应填上
        assertTrue(csv.contains("gpt-4-alias"), csv);
        assertTrue(csv.contains("gpt-4-actual"), csv);
        assertTrue(csv.contains("100003"), csv);
    }

    @Test
    void audit_log_row_handles_minimal_log() throws Exception {
        AuditLog log = AuditLog.builder().id("log-002").build();  // 仅 id
        AuditLogExportController.AuditLogRow row = new AuditLogExportController.AuditLogRow(log);
        String json = new ObjectMapper().writeValueAsString(row);

        assertNotNull(json);
        assertTrue(json.contains("\"id\":\"log-002\""), json);
    }

    // ---- MAX_EXPORT_ROWS 上限 ----

    @Test
    void max_export_rows_constant() {
        assertEquals(100_000L, AuditLogExportController.MAX_EXPORT_ROWS,
                "MAX_EXPORT_ROWS 上限应明示 100k 防拖库");
    }

    @Test
    void export_pages_through_repository_and_pushes_filters_to_query() throws Exception {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuditLogExportController controller =
                new AuditLogExportController(repository, new ObjectMapper());
        AuditLog first = AuditLog.builder().id("log-1").userId("user-1").endpoint("/one").build();
        AuditLog second = AuditLog.builder().id("log-2").userId("user-1").endpoint("/two").build();

        when(repository.findForExport(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq("user-1"),
                eq("key-1"),
                any(PageRequest.class)
        )).thenReturn(
                new PageImpl<>(List.of(first), PageRequest.of(0, 1_000), 1_001),
                new PageImpl<>(List.of(second), PageRequest.of(1, 1_000), 1_001)
        );

        StreamingResponseBody body = controller.export(
                "csv",
                "2026-06-01T00:00:00",
                "2026-06-15T00:00:00",
                "user-1",
                "key-1",
                response
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        body.writeTo(output);

        verify(repository, times(2)).findForExport(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq("user-1"),
                eq("key-1"),
                any(PageRequest.class)
        );
        String csv = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("log-1"), csv);
        assertTrue(csv.contains("log-2"), csv);
    }
}
