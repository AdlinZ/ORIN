package com.adlin.orin.modules.endpoint.controller;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.endpoint.dto.ExecuteEndpointRequest;
import com.adlin.orin.modules.endpoint.dto.ExecuteEndpointResponse;
import com.adlin.orin.modules.endpoint.service.EndpointExecutionService;
import com.adlin.orin.modules.run.entity.Run;
import com.adlin.orin.modules.run.repository.RunRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * F05 Endpoint 外部执行控制器（REST）。
 *
 * <p>Base: /v1/endpoints（API Key 鉴权，由 {@code ApiKeyAuthInterceptor} 统一处理）。
 *
 * <p>ADR-003 同步/异步/流式契约的 REST 实现。
 */
@RestController
@RequestMapping("/v1/endpoints")
@RequiredArgsConstructor
public class EndpointExecutionController {

    private final EndpointExecutionService executionService;
    private final RunRepository runRepository;

    /**
     * 调用已发布的 Endpoint。
     *
     * <p>API Key 由 {@code ApiKeyAuthInterceptor} 注入到 request attribute。
     */
    @PostMapping("/{endpointId}/run")
    public ResponseEntity<?> execute(@PathVariable String endpointId,
                                      @Valid @RequestBody ExecuteEndpointRequest request,
                                      HttpServletRequest httpRequest) {
        GatewaySecret apiKey = extractApiKey(httpRequest);
        ExecuteEndpointResponse response = executionService.execute(endpointId, request, apiKey);

        // 异步模式返回 202
        if ("QUEUED".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 查询 Endpoint 下的 Run 状态（API Key 鉴权）。
     *
     * <p>仅允许查询属于该 API Key 的 Endpoint 产生的 Run。
     */
    @GetMapping("/{endpointId}/runs/{runId}")
    public ResponseEntity<?> getRun(@PathVariable String endpointId,
                                     @PathVariable String runId,
                                     HttpServletRequest httpRequest) {
        // 验证 API Key 存在（interceptor 已验证）
        GatewaySecret apiKey = extractApiKey(httpRequest);
        // 加载 Run
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND,
                        "Run 不存在: " + runId));
        // F05 P0：通过 endpointId 验证 Run 确实属于此 Endpoint
        if (run.getEndpointId() == null || !run.getEndpointId().equals(endpointId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error("ENDPOINT_ACCESS_DENIED",
                            "Run 不属于此 Endpoint"));
        }
        // 验证 Run 由 API Key 创建
        if (run.getCreatedBy() == null
                || !run.getCreatedBy().startsWith("api-key:")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error("ENDPOINT_ACCESS_DENIED", "Run 不是由 API Key 创建"));
        }
        // 验证 API Key 匹配
        String keyId = apiKey.getSecretId();
        if (!run.getCreatedBy().contains(keyId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error("ENDPOINT_ACCESS_DENIED",
                            "API Key 无权查看此 Run"));
        }

        return ResponseEntity.ok(Map.of(
                "runId", run.getId(),
                "traceId", run.getTraceId(),
                "status", run.getStatus().name(),
                "output", run.getOutput() != null ? run.getOutput() : "",
                "errorMessage", run.getErrorMessage() != null ? run.getErrorMessage() : "",
                "createdAt", run.getCreatedAt()
        ));
    }

    private GatewaySecret extractApiKey(HttpServletRequest request) {
        Object attr = request.getAttribute("apiKey");
        if (!(attr instanceof GatewaySecret secret)) {
            throw new BusinessException(ErrorCode.AUTH_API_KEY_INVALID,
                    "Missing or invalid API key");
        }
        return secret;
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "code", code,
                "message", message,
                "traceId", UUID.randomUUID().toString()
        );
    }
}
