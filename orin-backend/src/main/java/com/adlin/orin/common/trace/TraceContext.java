package com.adlin.orin.common.trace;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * W3C Trace Context 传播工具，集中放给后端所有跨边界链路（HTTP / MQ / 异步）复用。
 *
 * <p>调用语义：
 * <ul>
 *   <li>{@link #build(String, String)} 把 traceId + spanId 组成 W3C traceparent 头值</li>
 *   <li>{@link #parse(String)} 反向：入站 header → {@link Traceparent}，非法返回 null</li>
 *   <li>{@link #isValid(String)} 单纯做格式校验</li>
 *   <li>{@link #generateSpanId()} 16-char lowercase hex 随机 span-id（用 {@link SecureRandom}）</li>
 *   <li>{@link #buildFromMdc()} 拿当前线程 MDC 里的 traceId 生成新 traceparent（缺则 UUID 兜底）</li>
 * </ul>
 *
 * <p>与 {@code security.TraceIdFilter} 的关系：本类自包含的 1 行 build 重复，刻意不依赖
 * security 包，避免 common 反向依赖 security。未来 refactor 切片再统一。
 *
 * <p>MDC key 名 {@code traceId} / {@code spanId} 与 {@code TraceIdFilter} 同步，
 * 保证所有日志 appender 看到的 key 一致。
 */
public final class TraceContext {

    /** W3C 标准 traceparent header。 */
    public static final String TRACEPARENT_HEADER = "traceparent";
    /** 兼容旧协议：ORIN 内部 X-Trace-Id header（仅 32 hex traceId）。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** MDC key：traceId（与 TraceIdFilter 同步）。 */
    public static final String TRACE_ID_KEY = "traceId";
    /** MDC key：spanId（与 TraceIdFilter 同步）。 */
    public static final String SPAN_ID_KEY = "spanId";

    private static final Pattern TRACEPARENT_REGEX =
            Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceContext() {
    }

    /**
     * W3C traceparent 解析结果。traceId=32 hex，spanId=16 hex，flags 通常是 01（sampled）。
     */
    public record Traceparent(String traceId, String spanId) {
    }

    /**
     * 组装 W3C traceparent 头值。
     *
     * @return 形如 {@code 00-<32hex>-<16hex>-01}
     */
    public static String build(String traceId, String spanId) {
        return "00-" + traceId + "-" + spanId + "-01";
    }

    /**
     * 解析 W3C traceparent header；非法返回 null。
     */
    public static Traceparent parse(String header) {
        if (header == null || !isValid(header)) {
            return null;
        }
        // 格式固定：00-<32>-<16>-<2>，索引 3..35 是 traceId，36..52 是 spanId
        return new Traceparent(header.substring(3, 35), header.substring(36, 52));
    }

    /**
     * 校验字符串是否为合法 W3C traceparent（00-32hex-16hex-2hex）。
     */
    public static boolean isValid(String header) {
        return header != null && TRACEPARENT_REGEX.matcher(header).matches();
    }

    /**
     * 16-char lowercase hex 随机 span-id。W3C 要求每 span 唯一。
     */
    public static String generateSpanId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 用当前 MDC 里的 traceId 拼一个出站 traceparent（每次新生成 spanId）。
     *
     * <p>MDC.traceId 缺失时用 UUID 兜底（与 {@code TraceIdFilter.parseTraceId} L91 一致）。
     * 适用于：生产者无上游 HTTP trace 上下文（定时任务 / DLQ replay / admin 工具）。
     */
    public static String buildFromMdc() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return build(traceId, generateSpanId());
    }
}
