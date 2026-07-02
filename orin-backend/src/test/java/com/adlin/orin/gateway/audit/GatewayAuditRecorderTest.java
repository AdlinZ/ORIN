package com.adlin.orin.gateway.audit;

import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Gateway-1b: GatewayAuditRecorder 单元测试。
 *
 * 覆盖：
 * - recordSuccess 调 logApiCall 第 6 重载，参数映射正确，status=200
 * - recordError(503, ...) 调 logApiCall，errorCode=SERVICE_UNAVAILABLE
 * - recordError(500, ...) 调 logApiCall，errorCode 来自 caller
 * - 异常 swallow：logApiCall 抛异常不传播
 * - providerModel 1b 简化策略：caller 传什么写什么
 * - null ctx 防御
 */
@ExtendWith(MockitoExtension.class)
class GatewayAuditRecorderTest {

    @Mock
    private AuditLogService auditLogService;

    private GatewayAuditRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new GatewayAuditRecorder(auditLogService);
    }

    @Test
    void recordSuccess_invokes_logApiCall_with_status_200_and_full_field_mapping() {
        recorder.recordSuccess(baseContext().build());

        // 25 参数：用 eq() 严格校验关键字段，any() 兜底次要字段
        verify(auditLogService).logApiCall(
                eq("user-1"),                                    // userId
                eq("key-1"),                                     // apiKeyId
                eq("openai"),                                    // providerId
                eq("openai"),                                    // providerType
                eq("/v1/chat/completions"),                      // endpoint
                eq("POST"),                                      // method
                eq("gpt-4"),                                     // modelAlias
                eq("gpt-4"),                                     // providerModel
                eq("127.0.0.1"),                                 // ipAddress
                eq("curl/7.85"),                                 // userAgent
                isNull(),                                        // requestParams
                isNull(),                                        // responseContent
                eq(200),                                         // statusCode
                eq(120L),                                        // responseTime (latency)
                eq(10),                                          // promptTokens
                eq(20),                                          // completionTokens
                isNull(),                                        // estimatedCost (service 重算)
                eq(true),                                        // success
                isNull(),                                        // errorMessage
                isNull(),                                        // errorCode
                isNull(),                                        // workflowId
                isNull(),                                        // conversationId
                isNull(),                                        // fileId
                isNull(),                                        // downloadUrl
                eq("trace-1"));                                  // traceId
    }

    @Test
    void recordError_503_invokes_logApiCall_with_service_unavailable_code() {
        recorder.recordError(baseContext().build(), 503,
                "No available provider", ErrorCode.SERVICE_UNAVAILABLE.getCode());

        verify(auditLogService).logApiCall(
                anyString(), anyString(), anyString(), anyString(),       // userId/apiKeyId/providerId/providerType
                eq("/v1/chat/completions"), eq("POST"),                   // endpoint/method
                anyString(), anyString(), anyString(), anyString(),       // alias/model/ip/ua
                any(), any(),                                              // request/response content
                eq(503),                                                   // statusCode
                anyLong(),                                                 // latency
                any(), any(),                                              // prompt/completion tokens
                isNull(),                                                  // estimatedCost
                eq(false),                                                 // success
                eq("No available provider"),                                // errorMessage
                eq(ErrorCode.SERVICE_UNAVAILABLE.getCode()),                // errorCode
                isNull(), isNull(), isNull(), isNull(),                    // workflowId/conversationId/fileId/downloadUrl
                anyString());                                               // traceId
    }

    @Test
    void recordError_500_invokes_logApiCall_with_throwable_mapped_code() {
        recorder.recordError(baseContext().build(), 500,
                "boom", ErrorCode.MODEL_API_ERROR.getCode());

        verify(auditLogService).logApiCall(
                anyString(), anyString(), anyString(), anyString(),
                eq("/v1/chat/completions"), eq("POST"),
                anyString(), anyString(), anyString(), anyString(),
                any(), any(),
                eq(500),
                anyLong(), any(), any(), isNull(),
                eq(false),
                eq("boom"),
                eq(ErrorCode.MODEL_API_ERROR.getCode()),
                isNull(), isNull(), isNull(), isNull(),
                anyString());
    }

    @Test
    void write_swallows_audit_log_service_exceptions() {
        // logApiCall 25 参数，stub 用 any() 全匹配（Double/Boolean 可能是 null，用 any() 不用 anyDouble/anyBoolean）
        doThrow(new RuntimeException("db down")).when(auditLogService).logApiCall(
                any(), any(), any(), any(),
                any(), any(),
                any(), any(), any(), any(),
                any(), any(),
                any(), any(), any(), any(),
                any(), any(),
                any(), any(),
                any(), any(), any(), any(), any());

        // 不应抛异常
        assertDoesNotThrow(() -> recorder.recordSuccess(baseContext().build()));
        // 仍然调用了一次 logApiCall
        verify(auditLogService).logApiCall(
                any(), any(), any(), any(),
                any(), any(),
                any(), any(), any(), any(),
                any(), any(),
                any(), any(), any(), any(),
                any(), any(),
                any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void recordSuccess_with_null_provider_fields_still_invokes_logApiCall() {
        // 失败路径 ctx：providerId/providerType/providerModel 为 null
        GatewayAuditContext ctx = GatewayAuditContext.builder()
                .userId("user-1").apiKeyId("key-1")
                .providerId(null).providerType(null)
                .modelAlias("gpt-4").providerModel(null)
                .traceId("trace-1").latencyMs(50L)
                .ipAddress("127.0.0.1").userAgent("curl/7.85")
                .build();

        recorder.recordSuccess(ctx);

        verify(auditLogService).logApiCall(
                eq("user-1"), eq("key-1"),
                isNull(), isNull(),                          // providerId/providerType 透传 null
                any(), any(),
                eq("gpt-4"), isNull(),                        // alias 透传，providerModel 透传 null
                any(), any(),
                any(), any(), any(), anyLong(),
                any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(),
                eq("trace-1"));
    }

    @Test
    void recordSuccess_uses_caller_supplied_providerModel_even_if_different_from_alias() {
        // 未来 Gateway-2a：caller 传真实 providerModel。1b 简化：caller 说什么就写什么。
        GatewayAuditContext ctx = baseContext()
                .modelAlias("alias-x")
                .providerModel("actual-y")  // 显式不同
                .build();

        recorder.recordSuccess(ctx);

        verify(auditLogService).logApiCall(
                any(), any(), any(), any(),
                any(), any(),
                eq("alias-x"),                            // modelAlias
                eq("actual-y"),                            // providerModel（不是 alias）
                any(), any(),
                any(), any(), anyInt(), anyLong(),
                any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(),
                any());
    }

    @Test
    void recordError_does_not_leak_interactions_on_null_ctx() {
        // 防御：null ctx 仍要 swallow，不传播 NullPointerException
        assertDoesNotThrow(() -> recorder.recordError(null, 500, "null ctx test", "10000"));
        verifyNoMoreInteractions(auditLogService);
    }

    private GatewayAuditContext.GatewayAuditContextBuilder baseContext() {
        return GatewayAuditContext.builder()
                .userId("user-1").apiKeyId("key-1")
                .providerId("openai").providerType("openai")
                .modelAlias("gpt-4").providerModel("gpt-4")
                .traceId("trace-1").latencyMs(120L)
                .promptTokens(10).completionTokens(20).totalTokens(30)
                .ipAddress("127.0.0.1").userAgent("curl/7.85");
    }
}
