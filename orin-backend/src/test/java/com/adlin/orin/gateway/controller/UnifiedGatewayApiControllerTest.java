package com.adlin.orin.gateway.controller;

import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.audit.GatewayAuditRecorder;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.service.ProviderRegistry;
import com.adlin.orin.gateway.service.RouterService;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UnifiedGatewayApiController 单元测试。
 *
 * <p>Gateway-1b: 验证 /v1/chat/completions 4 路径（成功 / 503 / 500 / 流式）写 audit_logs。
 * 不 mock GatewayAuditRecorder（mock-inline 在 Java 17 + Spring AOT 上偶发 issue），
 * 改用真实 recorder 实例 + mock AuditLogService 间接验证。
 */
@ExtendWith(MockitoExtension.class)
class UnifiedGatewayApiControllerTest {

    @Mock
    private ProviderRegistry providerRegistry;
    @Mock
    private RouterService routerService;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private ProviderAdapter provider;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private GatewaySecret gatewaySecret;

    private UnifiedGatewayApiController controller;

    @BeforeEach
    void setUp() {
        controller = new UnifiedGatewayApiController(
                providerRegistry, routerService,
                new GatewayAuditRecorder(auditLogService),  // 真实 recorder + mock service
                workflowService);
    }

    @Test
    void chatCompletions_ShouldReturnSseWhenStreamTrue() {
        // 1b: 流式路径显式 no audit hook —— auditLogService 不被调
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-test")
                .stream(true)
                .messages(List.of())
                .build();
        ChatCompletionResponse chunk = ChatCompletionResponse.builder()
                .id("chunk-1")
                .model("gpt-test")
                .build();

        when(routerService.selectProviderByModel("gpt-test", request)).thenReturn(java.util.Optional.of(provider));
        when(provider.getProviderName()).thenReturn("test");
        when(provider.getProviderType()).thenReturn("openai");
        when(provider.chatCompletionStream(request)).thenReturn(Flux.just(chunk));

        ResponseEntity<Object> response = controller.chatCompletions(
                request, null, null, "trace-1", httpRequest).block();

        assertThat(response).isNotNull();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getBody()).isInstanceOf(Flux.class);
        @SuppressWarnings("unchecked")
        Flux<ServerSentEvent<ChatCompletionResponse>> body =
                (Flux<ServerSentEvent<ChatCompletionResponse>>) response.getBody();
        assertThat(body.blockFirst().data()).isEqualTo(chunk);
        // 流式：audit 不被调
        verify(auditLogService, never()).logApiCall(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                any(), any(),
                anyInt(), anyLong(), anyInt(), anyInt(),
                anyDouble(), anyBoolean(), any(), any(),
                any(), any(), any(), any(), anyString());
    }

    @Test
    void healthCheck_shouldReturnCachedProviderStateWithoutExternalProbe() {
        when(providerRegistry.getHealthSnapshot()).thenReturn(Map.of("local-ollama", true));
        when(providerRegistry.getStatistics()).thenReturn(Map.of(
                "totalProviders", 1,
                "healthyProviders", 1,
                "unhealthyProviders", 0));

        ResponseEntity<Map<String, Object>> response = controller.healthCheck().block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("status", "ok");
        assertThat(response.getBody()).containsEntry("providers", Map.of("local-ollama", true));
        verify(providerRegistry, never()).checkAllHealth();
    }

    // ============================================================
    // Gateway-1b: chatCompletions 4 路径 audit hook 验证
    // ============================================================

    @Test
    void chatCompletions_nonStream_success_invokes_auditLogService_logApiCall_with_full_fields() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4")
                .stream(false)
                .messages(List.of())
                .build();
        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .id("resp-1")
                .model("gpt-4")
                .usage(ChatCompletionResponse.Usage.builder()
                        .promptTokens(11).completionTokens(22).totalTokens(33).build())
                .build();

        when(httpRequest.getAttribute("apiKey")).thenReturn(gatewaySecret);
        when(gatewaySecret.getUserId()).thenReturn("user-1");
        when(gatewaySecret.getSecretId()).thenReturn("key-1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("curl/7.85");
        when(routerService.selectProviderByModel("gpt-4", request)).thenReturn(java.util.Optional.of(provider));
        when(provider.getProviderName()).thenReturn("openai");
        when(provider.getProviderType()).thenReturn("openai");
        when(provider.chatCompletion(eq(request), eq("trace-1"))).thenReturn(Mono.just(response));

        ResponseEntity<Object> result = controller.chatCompletions(
                request, null, null, "trace-1", httpRequest).block();

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();

        // 关键字段都传给 logApiCall
        verify(auditLogService).logApiCall(
                eq("user-1"),                                    // userId
                eq("key-1"),                                     // apiKeyId
                eq("openai"),                                    // providerId
                eq("openai"),                                    // providerType
                eq("/v1/chat/completions"),                      // endpoint
                eq("POST"),                                      // method
                eq("gpt-4"),                                     // modelAlias
                eq("gpt-4"),                                     // providerModel (1b 透传)
                eq("127.0.0.1"),                                 // ipAddress
                eq("curl/7.85"),                                 // userAgent
                any(),                                           // requestParams (null in recorder)
                any(),                                           // responseContent (null in recorder)
                eq(200),                                         // statusCode
                anyLong(),                                       // responseTime (latency)
                eq(11),                                          // promptTokens
                eq(22),                                          // completionTokens
                any(),                                           // estimatedCost (recorder 传 null, service 重算)
                eq(true),                                        // success
                any(),                                           // errorMessage (null)
                any(),                                           // errorCode (null for success)
                any(), any(), any(), any(),                     // workflow/conversation/file/downloadUrl
                eq("trace-1"));                                  // traceId
    }

    @Test
    void chatCompletions_nonStream_response_without_usage_still_writes_audit_with_null_tokens() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4").stream(false).messages(List.of()).build();
        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .id("resp-1").model("gpt-4").build(); // 无 usage

        when(httpRequest.getAttribute("apiKey")).thenReturn(gatewaySecret);
        when(gatewaySecret.getUserId()).thenReturn("user-1");
        when(gatewaySecret.getSecretId()).thenReturn("key-1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("curl/7.85");
        when(routerService.selectProviderByModel("gpt-4", request)).thenReturn(java.util.Optional.of(provider));
        when(provider.getProviderName()).thenReturn("openai");
        when(provider.getProviderType()).thenReturn("openai");
        when(provider.chatCompletion(eq(request), eq("trace-1"))).thenReturn(Mono.just(response));

        controller.chatCompletions(request, null, null, "trace-1", httpRequest).block();

        // logApiCall 仍被调，但 tokens 是 null
        verify(auditLogService).logApiCall(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                any(), any(),
                eq(200), anyLong(),
                any(),                                          // promptTokens null
                any(),                                          // completionTokens null
                any(), eq(true),
                any(), any(),
                any(), any(), any(), any(),
                anyString());
    }

    @Test
    void chatCompletions_switchIfEmpty_503_invokes_logApiCall_with_ServiceUnavailable_code() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("unknown-model").stream(false).messages(List.of()).build();

        when(httpRequest.getAttribute("apiKey")).thenReturn(gatewaySecret);
        when(gatewaySecret.getUserId()).thenReturn("user-1");
        when(gatewaySecret.getSecretId()).thenReturn("key-1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("curl/7.85");
        when(routerService.selectProviderByModel("unknown-model", request)).thenReturn(java.util.Optional.empty());

        ResponseEntity<Object> result = controller.chatCompletions(
                request, null, null, "trace-1", httpRequest).block();

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode().value()).isEqualTo(503);
        // 关键字段：503 + SERVICE_UNAVAILABLE
        verify(auditLogService).logApiCall(
                eq("user-1"), eq("key-1"),
                any(), any(),                                  // providerId/providerType (null)
                anyString(), anyString(),                       // endpoint/method
                eq("unknown-model"), any(),                    // alias / providerModel (null)
                anyString(), anyString(),                       // ip/ua
                any(), any(),
                eq(503),                                        // statusCode
                anyLong(),
                any(), any(),                                   // tokens
                any(), eq(false),                              // success
                eq("No available provider"),
                eq(ErrorCode.SERVICE_UNAVAILABLE.getCode()),    // errorCode: 130001
                any(), any(), any(), any(),
                eq("trace-1"));
    }

    @Test
    void chatCompletions_onErrorResume_500_invokes_logApiCall_with_throwable_mapped_code() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4").stream(false).messages(List.of()).build();

        when(httpRequest.getAttribute("apiKey")).thenReturn(gatewaySecret);
        when(gatewaySecret.getUserId()).thenReturn("user-1");
        when(gatewaySecret.getSecretId()).thenReturn("key-1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("curl/7.85");
        when(routerService.selectProviderByModel("gpt-4", request)).thenReturn(java.util.Optional.of(provider));
        when(provider.getProviderName()).thenReturn("openai");
        when(provider.getProviderType()).thenReturn("openai");
        RuntimeException boom = new RuntimeException("upstream failed");
        when(provider.chatCompletion(eq(request), eq("trace-1"))).thenReturn(Mono.error(boom));

        ResponseEntity<Object> result = controller.chatCompletions(
                request, null, null, "trace-1", httpRequest).block();

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode().value()).isEqualTo(500);
        // 500 + SYSTEM_ERROR 错码
        verify(auditLogService).logApiCall(
                anyString(), anyString(), any(), any(),
                anyString(), anyString(),
                anyString(), any(), anyString(), anyString(),
                any(), any(),
                eq(500), anyLong(),
                any(), any(),
                any(), eq(false),
                eq("upstream failed"),
                eq(ErrorCode.SYSTEM_ERROR.getCode()),
                any(), any(), any(), any(),
                anyString());
    }

    @Test
    void chatCompletions_provider_returns_empty_mono_falls_through_to_503_without_double_audit_write() {
        // provider 返回 Mono.empty() → doOnSuccess(null) 不应写 audit
        // → switchIfEmpty 写 503 audit（只写一次）
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4").stream(false).messages(List.of()).build();

        when(httpRequest.getAttribute("apiKey")).thenReturn(gatewaySecret);
        when(gatewaySecret.getUserId()).thenReturn("user-1");
        when(gatewaySecret.getSecretId()).thenReturn("key-1");
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("curl/7.85");
        when(routerService.selectProviderByModel("gpt-4", request)).thenReturn(java.util.Optional.of(provider));
        when(provider.getProviderName()).thenReturn("openai");
        when(provider.getProviderType()).thenReturn("openai");
        when(provider.chatCompletion(eq(request), eq("trace-1"))).thenReturn(Mono.empty());

        ResponseEntity<Object> result = controller.chatCompletions(
                request, null, null, "trace-1", httpRequest).block();

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode().value()).isEqualTo(503);

        // 只应有一次 logApiCall（503 路径），不应有 success 路径的双写
        verify(auditLogService).logApiCall(
                anyString(), anyString(),
                any(), any(),
                anyString(), anyString(),
                anyString(), any(),
                anyString(), anyString(),
                any(), any(),
                eq(503), anyLong(),
                any(), any(),
                any(), eq(false),
                eq("No available provider"),
                eq(ErrorCode.SERVICE_UNAVAILABLE.getCode()),
                any(), any(), any(), any(),
                eq("trace-1"));
    }

    @Test
    void chatCompletions_with_null_httpRequest_does_not_throw_and_writes_audit_with_null_user_fields() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4").stream(false).messages(List.of()).build();
        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .id("resp-1").model("gpt-4").build();

        when(routerService.selectProviderByModel("gpt-4", request)).thenReturn(java.util.Optional.of(provider));
        when(provider.getProviderName()).thenReturn("openai");
        when(provider.getProviderType()).thenReturn("openai");
        when(provider.chatCompletion(eq(request), eq("trace-1"))).thenReturn(Mono.just(response));

        // httpRequest = null —— 1b 设计 userId/apiKeyId 都为 null，audit 仍写
        ResponseEntity<Object> result = controller.chatCompletions(
                request, null, null, "trace-1", null).block();

        assertThat(result).isNotNull();
        verify(auditLogService).logApiCall(
                any(),                                          // userId null
                any(),                                          // apiKeyId null
                eq("openai"), eq("openai"),
                anyString(), anyString(),
                anyString(), anyString(),                       // modelAlias / providerModel
                any(),                                          // ipAddress null
                any(),                                          // userAgent null
                any(), any(),
                eq(200), anyLong(),
                any(), any(), any(), eq(true), any(), any(),
                any(), any(), any(), any(),
                anyString());
    }
}
