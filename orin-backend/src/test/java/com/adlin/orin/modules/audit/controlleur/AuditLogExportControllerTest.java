package com.adlin.orin.modules.audit.controlleur;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
                .ipAddress("10.0.0.1")
                .userAgent("curl/7.85")
                .requestParams("{\"messages\":[]}")
                .responseContent("{}")
                .statusCode(200)
                .responseTime(123L)
                .promptTokens(10)
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
}
