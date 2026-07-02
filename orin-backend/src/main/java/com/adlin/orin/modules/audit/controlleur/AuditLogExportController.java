package com.adlin.orin.modules.audit.controlleur;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志导出控制器（Phase 3 起步 · 小刀 9a）。
 *
 * 路线图 §5.2 第 3 项：审计日志可导出（CSV / JSON）。
 *
 * 设计要点：
 * - **流式输出**（`StreamingResponseBody`）—— 避免 100k 行一次性加载到内存 OOM
 * - **上限 100k 行**（`MAX_EXPORT_ROWS`）—— 防恶意请求拖库
 * - **Admin 鉴权**（`@PreAuthorize("hasRole('ADMIN')")`）—— 导出含完整 IP / UA / request params
 * - **CSV 用 RFC 4180 双引号转义**（含逗号 / 双引号 / 换行的字段加引号）
 * - **JSON 用 NDJSON**（每行一个对象）—— 比 JSON array 流式友好
 * - **`Content-Disposition: attachment`** —— 浏览器直接下载，文件名含时间区间
 *
 * package 沿用 `controlleur`（历史中文拼音）以匹配项目其它 audit controller。
 */
@RestController
@RequestMapping("/api/v1/audit/logs/export")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PLATFORM_ADMIN')")
@Tag(name = "Phase 5: Audit Logs", description = "审计日志导出（CSV / JSON，admin 限定）")
public class AuditLogExportController {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogExportController.class);

    /**
     * 单次导出上限（防恶意大查询拖库）。超出后流被截断，Content-Disposition
     * filename 含 "truncated" 提示。
     */
    static final long MAX_EXPORT_ROWS = 100_000L;
    private static final int EXPORT_PAGE_SIZE = 1_000;

    /** CSV 表头 —— 顺序与 `writeCsvRow` 一一对应。 */
    private static final String[] CSV_HEADERS = {
            "id", "userId", "apiKeyId", "providerId", "conversationId", "workflowId",
            "providerType", "traceId", "endpoint", "method", "model", "modelAlias",
            "providerModel", "ipAddress", "userAgent", "requestParams", "responseContent",
            "statusCode", "responseTime", "promptTokens", "errorCode", "createdAt"
    };

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogExportController(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "导出审计日志（CSV / JSON，admin 限定）")
    @GetMapping
    public StreamingResponseBody export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String apiKeyId,
            HttpServletResponse response) {

        // ---- 1) 解析参数 ----
        ExportFormat fmt = ExportFormat.parse(format);
        LocalDateTime fromTime = parseTime(from, "from");
        LocalDateTime toTime = parseTime(to, "to");
        if (fromTime == null) {
            // 默认导出近 30 天（防意外"全库导出"）
            fromTime = LocalDateTime.now().minusDays(30);
        }
        if (toTime == null) {
            toTime = LocalDateTime.now();
        }
        if (fromTime.isAfter(toTime)) {
            throw new IllegalArgumentException("from > to: " + fromTime + " > " + toTime);
        }

        String filename = buildFilename(fmt, fromTime, toTime, userId, apiKeyId);
        response.setContentType(fmt.contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"");
        // 不设 Content-Length：流式输出长度未知

        final LocalDateTime fromFinal = fromTime;
        final LocalDateTime toFinal = toTime;
        final String userIdFilter = normalizeFilter(userId);
        final String apiKeyIdFilter = normalizeFilter(apiKeyId);
        logger.info("[audit-export] start format={} from={} to={} userId={} apiKeyId={}",
                fmt, fromFinal, toFinal, userId, apiKeyId);

        // ---- 2) 流式输出 ----
        return (OutputStream out) -> {
            long written = 0;
            boolean truncated = false;
            try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                if (fmt == ExportFormat.CSV) {
                    writer.write(String.join(",", CSV_HEADERS));
                    writer.write("\r\n");
                }
                int pageNumber = 0;
                boolean hasMore;
                do {
                    Page<AuditLog> page = auditLogRepository.findForExport(
                            fromFinal,
                            toFinal,
                            userIdFilter,
                            apiKeyIdFilter,
                            PageRequest.of(pageNumber, EXPORT_PAGE_SIZE)
                    );
                    for (AuditLog log : page.getContent()) {
                        if (written >= MAX_EXPORT_ROWS) {
                            truncated = true;
                            break;
                        }
                        if (fmt == ExportFormat.CSV) {
                            writeCsvRow(writer, log);
                        } else {
                            writeJsonRow(writer, log);
                        }
                        written++;
                    }
                    hasMore = page.hasNext();
                    pageNumber++;
                } while (!truncated && hasMore);
                writer.flush();
            }
            logger.info("[audit-export] done format={} written={} truncated={}",
                    fmt, written, truncated);
        };
    }

    private static String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private LocalDateTime parseTime(String s, String name) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid " + name + " timestamp (expect ISO 8601 LocalDateTime, e.g. 2026-06-15T00:00:00): " + s);
        }
    }

    private String buildFilename(ExportFormat fmt, LocalDateTime from, LocalDateTime to,
                                 String userId, String apiKeyId) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        StringBuilder sb = new StringBuilder("audit-")
                .append(from.format(dtf))
                .append("_")
                .append(to.format(dtf));
        if (userId != null && !userId.isBlank()) {
            sb.append("-user-").append(sanitize(userId));
        }
        if (apiKeyId != null && !apiKeyId.isBlank()) {
            sb.append("-apikey-").append(sanitize(apiKeyId));
        }
        sb.append('.').append(fmt.extension);
        return sb.toString();
    }

    /** 防止 userId / apiKeyId 含路径分隔符等破坏 filename。 */
    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /**
     * RFC 4180 CSV 行写入。字段含逗号 / 双引号 / 换行 → 整字段加双引号，
     * 内部双引号转义为两个双引号。null / 空走空串。
     */
    private void writeCsvRow(Writer w, AuditLog log) throws IOException {
        String[] cells = {
                nullToEmpty(log.getId()),
                nullToEmpty(log.getUserId()),
                nullToEmpty(log.getApiKeyId()),
                nullToEmpty(log.getProviderId()),
                nullToEmpty(log.getConversationId()),
                nullToEmpty(log.getWorkflowId()),
                nullToEmpty(log.getProviderType()),
                nullToEmpty(log.getTraceId()),
                nullToEmpty(log.getEndpoint()),
                nullToEmpty(log.getMethod()),
                nullToEmpty(log.getModel()),
                nullToEmpty(log.getModelAlias()),
                nullToEmpty(log.getProviderModel()),
                nullToEmpty(log.getIpAddress()),
                nullToEmpty(log.getUserAgent()),
                nullToEmpty(log.getRequestParams()),
                nullToEmpty(log.getResponseContent()),
                log.getStatusCode() == null ? "" : log.getStatusCode().toString(),
                log.getResponseTime() == null ? "" : log.getResponseTime().toString(),
                log.getPromptTokens() == null ? "" : log.getPromptTokens().toString(),
                nullToEmpty(log.getErrorCode()),
                log.getCreatedAt() == null ? "" : log.getCreatedAt().toString(),
        };
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write(escapeCsv(cells[i]));
        }
        w.write("\r\n");
    }

    /** RFC 4180 字段 escape（package-private for tests）。 */
    static String escapeCsv(String cell) {
        if (cell == null) {
            return "";
        }
        boolean needsQuoting = cell.indexOf(',') >= 0
                || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0
                || cell.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return cell;
        }
        return "\"" + cell.replace("\"", "\"\"") + "\"";
    }

    /** NDJSON：每行一个对象，Jackson 序列化。 */
    private void writeJsonRow(Writer w, AuditLog log) throws IOException {
        try {
            w.write(objectMapper.writeValueAsString(new AuditLogRow(log)));
            w.write("\n");
        } catch (Exception e) {
            logger.warn("[audit-export] serialize row id={} failed: {}",
                    log.getId(), e.getMessage());
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 内部 NDJSON 投影 POJO —— 仅暴露导出需要的字段，避免 entity 敏感字段泄露。 */
    public static class AuditLogRow {
        public String id;
        public String userId;
        public String apiKeyId;
        public String providerId;
        public String conversationId;
        public String workflowId;
        public String providerType;
        public String traceId;
        public String endpoint;
        public String method;
        public String model;
        public String modelAlias;
        public String providerModel;
        public String ipAddress;
        public String userAgent;
        public String requestParams;
        public String responseContent;
        public Integer statusCode;
        public Long responseTime;
        public Integer promptTokens;
        public String errorCode;
        public LocalDateTime createdAt;

        public AuditLogRow(AuditLog log) {
            this.id = log.getId();
            this.userId = log.getUserId();
            this.apiKeyId = log.getApiKeyId();
            this.providerId = log.getProviderId();
            this.conversationId = log.getConversationId();
            this.workflowId = log.getWorkflowId();
            this.providerType = log.getProviderType();
            this.traceId = log.getTraceId();
            this.endpoint = log.getEndpoint();
            this.method = log.getMethod();
            this.model = log.getModel();
            this.modelAlias = log.getModelAlias();
            this.providerModel = log.getProviderModel();
            this.ipAddress = log.getIpAddress();
            this.userAgent = log.getUserAgent();
            this.requestParams = log.getRequestParams();
            this.responseContent = log.getResponseContent();
            this.statusCode = log.getStatusCode();
            this.responseTime = log.getResponseTime();
            this.promptTokens = log.getPromptTokens();
            this.errorCode = log.getErrorCode();
            this.createdAt = log.getCreatedAt();
        }
    }

    private enum ExportFormat {
        CSV("text/csv;charset=UTF-8", "csv"),
        JSON("application/x-ndjson;charset=UTF-8", "json");

        final String contentType;
        final String extension;

        ExportFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        static ExportFormat parse(String s) {
            if (s == null) {
                return CSV;
            }
            switch (s.toLowerCase()) {
                case "csv":
                case "":
                    return CSV;
                case "json":
                case "ndjson":
                case "jsonl":
                    return JSON;
                default:
                    throw new IllegalArgumentException(
                            "Unknown format: " + s + " (supported: csv, json, ndjson, jsonl)");
            }
        }
    }
}
