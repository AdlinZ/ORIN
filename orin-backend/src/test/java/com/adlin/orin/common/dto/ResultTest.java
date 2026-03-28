package com.adlin.orin.common.dto;

import com.adlin.orin.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F1.3 统一响应 Result 回归测试
 *
 * 测试目标：验证 Result<T> 静态工厂方法的全部变体
 * - success() / success(T) / success(T, String)
 * - error(ErrorCode) / error(ErrorCode, String) / error(String, String)
 * - traceId 字段存在且可设置
 * - timestamp 默认值存在
 * - metadata 字段存在
 *
 * 运行方式：mvn test -Dtest=ResultTest
 */
class ResultTest {

    // ==================== success 工厂方法 ====================

    @Test
    @DisplayName("F1.3 - success() 返回 code=00000，data=null")
    void testSuccess_NoData() {
        Result<String> result = Result.success();

        assertEquals("00000", result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("F1.3 - success(T data) 返回 code=00000，data 不为空")
    void testSuccess_WithData() {
        Result<String> result = Result.success("hello");

        assertEquals("00000", result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals("hello", result.getData());
    }

    @Test
    @DisplayName("F1.3 - success(T, String) 自定义消息不影响 code")
    void testSuccess_WithDataAndMessage() {
        Result<String> result = Result.success("data-value", "自定义成功消息");

        assertEquals("00000", result.getCode());
        assertEquals("自定义成功消息", result.getMessage());
        assertEquals("data-value", result.getData());
    }

    // ==================== error 工厂方法 ====================

    @Test
    @DisplayName("F1.3 - error(ErrorCode) 使用错误码的 code 和 message")
    void testError_ErrorCode() {
        Result<Object> result = Result.error(ErrorCode.AGENT_NOT_FOUND);

        assertEquals("30001", result.getCode());
        assertEquals("智能体未找到", result.getMessage());
    }

    @Test
    @DisplayName("F1.3 - error(ErrorCode, String) 覆盖 message 但保留 code")
    void testError_ErrorCodeWithCustomMessage() {
        Result<Object> result = Result.error(ErrorCode.WORKFLOW_EXECUTION_FAILED, "工作流执行超时");

        assertEquals("60002", result.getCode());
        assertEquals("工作流执行超时", result.getMessage());
    }

    @Test
    @DisplayName("F1.3 - error(String, String) 完全自定义 code 和 message")
    void testError_FullCustom() {
        Result<Object> result = Result.error("CUSTOM01", "完全自定义错误");

        assertEquals("CUSTOM01", result.getCode());
        assertEquals("完全自定义错误", result.getMessage());
    }

    // ==================== 字段存在性验证 ====================

    @Test
    @DisplayName("F1.3 - traceId 字段存在且可设置")
    void testTraceIdField() {
        Result<String> result = Result.<String>builder()
                .code("00000")
                .traceId("trace-12345")
                .build();

        assertEquals("trace-12345", result.getTraceId());
    }

    @Test
    @DisplayName("F1.3 - timestamp 默认值为当前时间（Builder.Default）")
    void testTimestampDefault() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        Result<Void> result = Result.success();
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertNotNull(result.getTimestamp());
        assertTrue(result.getTimestamp().isAfter(before));
        assertTrue(result.getTimestamp().isBefore(after));
    }

    @Test
    @DisplayName("F1.3 - metadata 字段可用于附加分页/验证错误信息")
    void testMetadataField() {
        Object metadata = java.util.Map.of("page", 1, "total", 100);
        Result<Void> result = Result.<Void>builder()
                .code("00000")
                .metadata(metadata)
                .build();

        assertNotNull(result.getMetadata());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> meta = (java.util.Map<String, Object>) result.getMetadata();
        assertEquals(1, meta.get("page"));
        assertEquals(100, meta.get("total"));
    }

    @Test
    @DisplayName("F1.3 - path 字段可用于记录请求路径")
    void testPathField() {
        Result<Void> result = Result.<Void>builder()
                .code("00000")
                .path("/api/agent/list")
                .build();

        assertEquals("/api/agent/list", result.getPath());
    }

    @Test
    @DisplayName("F1.3 - detail 字段存在")
    void testDetailField() {
        Result<Void> result = Result.<Void>builder()
                .code("10000")
                .detail("系统内部错误详情")
                .build();

        assertEquals("系统内部错误详情", result.getDetail());
    }
}
