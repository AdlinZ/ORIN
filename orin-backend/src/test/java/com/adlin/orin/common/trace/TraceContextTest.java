package com.adlin.orin.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextTest {

    @BeforeEach
    void clearMdc() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ---- build ----

    @Test
    void build_givenTraceIdAndSpanId_producesValidTraceparent() {
        String tp = TraceContext.build("0123456789abcdef0123456789abcdef", "0123456789abcdef");

        assertTrue(TraceContext.isValid(tp));
        assertEquals("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", tp);
    }

    // ---- parse ----

    @Test
    void parse_validHeader_returnsTraceparent() {
        TraceContext.Traceparent tp = TraceContext.parse(
                "00-0123456789abcdef0123456789abcdef-fedcba9876543210-01");

        assertNotNull(tp);
        assertEquals("0123456789abcdef0123456789abcdef", tp.traceId());
        assertEquals("fedcba9876543210", tp.spanId());
    }

    @Test
    void parse_invalidHeader_returnsNull() {
        assertNull(TraceContext.parse(null));
        assertNull(TraceContext.parse(""));
        assertNull(TraceContext.parse("not-a-traceparent"));
        assertNull(TraceContext.parse("00-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz-zzzzzzzzzzzzzzzz-01"));
        assertNull(TraceContext.parse("00-0123456789abcdef0123456789abcdef-0123456789abcdef"));
        assertNull(TraceContext.parse("01-0123456789abcdef0123456789abcdef-0123456789abcdef-01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz-fedcba9876543210-01", // 非 hex
            "00-0123456789abcdef0123456789abcdef-fedcba9876543210",    // 缺 flags
            "00-0123456789abcdef0123456789abcde-fedcba9876543210-01", // traceId 31
            "00-0123456789abcdef0123456789abcdef0-fedcba9876543210-01", // traceId 33
            "01-0123456789abcdef0123456789abcdef-fedcba9876543210-01"  // 非 00 版本
    })
    void isValid_rejectsMalformed(String header) {
        assertFalse(TraceContext.isValid(header));
    }

    // ---- generateSpanId ----

    @Test
    void generateSpanId_is16LowerHex() {
        String s1 = TraceContext.generateSpanId();
        String s2 = TraceContext.generateSpanId();

        assertEquals(16, s1.length());
        assertTrue(s1.matches("[0-9a-f]{16}"));
        assertNotEquals(s1, s2, "两次生成应不同");
    }

    // ---- buildFromMdc ----

    @Test
    void buildFromMdc_whenMdcEmpty_generatesFreshTraceparent() {
        String tp = TraceContext.buildFromMdc();

        assertTrue(TraceContext.isValid(tp));
        // 32 hex + 16 hex + 版本与 flag
        assertTrue(tp.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
    }

    @Test
    void buildFromMdc_whenMdcHasTraceId_preservesIt() {
        MDC.put(TraceContext.TRACE_ID_KEY, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa00");

        String tp = TraceContext.buildFromMdc();

        assertTrue(TraceContext.isValid(tp));
        // traceId 段位 3..35 应为 32 hex
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa00", tp.substring(3, 35));
        // span-id 段是随机 16 hex
        assertTrue(tp.substring(36, 52).matches("[0-9a-f]{16}"));
    }

    @Test
    void buildFromMdc_eachCall_returnsDistinctSpanId() {
        MDC.put(TraceContext.TRACE_ID_KEY, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbb00");

        String t1 = TraceContext.buildFromMdc();
        String t2 = TraceContext.buildFromMdc();

        assertEquals(t1.substring(3, 35), t2.substring(3, 35), "traceId 保持");
        assertNotEquals(t1.substring(36, 52), t2.substring(36, 52), "spanId 每次新生成");
    }
}
