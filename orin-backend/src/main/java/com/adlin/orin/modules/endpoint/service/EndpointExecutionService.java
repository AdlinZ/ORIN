package com.adlin.orin.modules.endpoint.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.endpoint.dto.ExecuteEndpointRequest;
import com.adlin.orin.modules.endpoint.dto.ExecuteEndpointResponse;
import com.adlin.orin.modules.endpoint.entity.AgentEndpoint;
import com.adlin.orin.modules.endpoint.entity.EndpointStatus;
import com.adlin.orin.modules.endpoint.repository.AgentEndpointRepository;
import com.adlin.orin.modules.run.dto.CreateRunRequest;
import com.adlin.orin.modules.run.dto.RunResponse;
import com.adlin.orin.modules.run.entity.Run;
import com.adlin.orin.modules.run.entity.RunEvent;
import com.adlin.orin.modules.run.repository.RunEventRepository;
import com.adlin.orin.modules.run.repository.RunRepository;
import com.adlin.orin.modules.run.service.RunService;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * F05 Endpoint 执行服务 — REST 和 MCP 共用。
 *
 * <p>核心闭环：验证 Endpoint + API Key → 分配 Runner → 创建 Run → 轮询直到完成 → 审计。
 *
 * <p>ADR-003 定义了完整的同步/异步/流式契约；第一刀实现同步和异步路径。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EndpointExecutionService {

    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final long POLL_INTERVAL_MS = 500;

    private final AgentEndpointRepository endpointRepository;
    private final RunnerRepository runnerRepository;
    private final RunService runService;
    private final RunRepository runRepository;
    private final RunEventRepository runEventRepository;
    private final AuditHelper auditHelper;
    private final ObjectMapper objectMapper;

    /**
     * 执行 Endpoint（同步或异步）。
     *
     * <p>注意：本方法不加 {@code @Transactional}，因为同步模式需要轮询 Run 状态
     * （最长 60s），不应持有数据库事务。内部的 {@code RunService.createRun()}
     * 和 {@code AuditHelper.log()} 各自管理自己的事务。
     *
     * @param endpointId Endpoint ID
     * @param request    执行请求（input, stream, timeoutMs）
     * @param apiKey     已验证的 API Key（由 {@code ApiKeyAuthInterceptor} 注入）
     * @return 执行响应（runId, traceId, status, output）
     */
    public ExecuteEndpointResponse execute(String endpointId,
                                            ExecuteEndpointRequest request,
                                            GatewaySecret apiKey) {
        // 1) 加载并校验 Endpoint
        AgentEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND,
                        "Endpoint 不存在: " + endpointId));
        if (endpoint.getStatus() != EndpointStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ENDPOINT_INACTIVE,
                    "Endpoint 已下线: " + endpoint.getStatus());
        }

        // 2) 校验 API Key 访问权限
        validateApiKeyAccess(endpoint, apiKey);

        // 3) 分配 Runner
        Runner runner = runnerRepository.findFirstByStatus(RunnerStatus.ONLINE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUNNER_UNAVAILABLE,
                        "没有可用的 Runner"));

        // 4) 创建 Run（绑定 endpointId 以便后续查询校验归属）
        String createdBy = "api-key:" + apiKey.getSecretId();
        CreateRunRequest createRequest = new CreateRunRequest();
        createRequest.setAgentId(endpoint.getAgentId());
        createRequest.setAgentVersionId(endpoint.getAgentVersionId());
        createRequest.setRunnerId(runner.getId());
        createRequest.setInput(request.getInput());
        createRequest.setEndpointId(endpointId);
        RunResponse runResponse = runService.createRun(createRequest, createdBy);

        String statusUrl = "/v1/endpoints/" + endpointId + "/runs/" + runResponse.getId();

        // 5) 审计
        auditHelper.log(apiKey.getUserId(),
                "ENDPOINT_EXECUTE",
                "/v1/endpoints/" + endpointId + "/run",
                "endpointId=" + endpointId
                        + ";apiKeyId=" + apiKey.getSecretId()
                        + ";runId=" + runResponse.getId()
                        + ";traceId=" + runResponse.getTraceId(),
                true, null);

        // 6) 异步模式：timeoutMs=0 立即返回 202
        long timeoutMs = request.getTimeoutMs() != null ? request.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
        if (timeoutMs <= 0) {
            return ExecuteEndpointResponse.builder()
                    .runId(runResponse.getId())
                    .traceId(runResponse.getTraceId())
                    .status("QUEUED")
                    .statusUrl(statusUrl)
                    .build();
        }

        // 7) 同步模式：轮询直到 Run 终态或超时
        return pollUntilTerminal(runResponse.getId(), runResponse.getTraceId(),
                timeoutMs, statusUrl);
    }

    /**
     * 校验 API Key 是否有权访问此 Endpoint。
     *
     * <p>从 {@code endpoint.config} JSON 的 {@code allowedApiKeyIds} 列表校验。
     * 如果 config 为空或 allowedApiKeyIds 为空，默认拒绝（安全优先）。
     */
    void validateApiKeyAccess(AgentEndpoint endpoint, GatewaySecret apiKey) {
        String configJson = endpoint.getConfig();
        if (configJson == null || configJson.isBlank()) {
            throw new BusinessException(ErrorCode.ENDPOINT_ACCESS_DENIED,
                    "Endpoint 未配置访问策略，拒绝访问");
        }
        try {
            Map<String, Object> config = objectMapper.readValue(configJson,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<String> allowedIds = (List<String>) config.get("allowedApiKeyIds");
            if (allowedIds == null || !allowedIds.contains(apiKey.getSecretId())) {
                throw new BusinessException(ErrorCode.ENDPOINT_ACCESS_DENIED,
                        "API Key 不在 Endpoint 允许列表中");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse endpoint config for access check: endpoint={}", endpoint.getId(), e);
            throw new BusinessException(ErrorCode.ENDPOINT_ACCESS_DENIED,
                    "Endpoint 配置解析失败");
        }
    }

    /**
     * 轮询 Run 直到终态或超时。
     */
    private ExecuteEndpointResponse pollUntilTerminal(String runId, String traceId,
                                                       long timeoutMs, String statusUrl) {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            Run run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                // Run 被删除
                return ExecuteEndpointResponse.builder()
                        .runId(runId)
                        .traceId(traceId)
                        .status("ERROR")
                        .output("Run 不存在")
                        .build();
            }

            if (run.getStatus().isTerminal()) {
                List<Map<String, Object>> events = loadEvents(runId);
                return ExecuteEndpointResponse.builder()
                        .runId(runId)
                        .traceId(traceId)
                        .status(run.getStatus().name())
                        .output(run.getOutput())
                        .events(events)
                        .build();
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 超时：返回当前状态 + statusUrl
        Run run = runRepository.findById(runId).orElse(null);
        String status = run != null ? run.getStatus().name() : "UNKNOWN";
        return ExecuteEndpointResponse.builder()
                .runId(runId)
                .traceId(traceId)
                .status(status)
                .statusUrl(statusUrl)
                .build();
    }

    private List<Map<String, Object>> loadEvents(String runId) {
        List<RunEvent> events = runEventRepository.findByRunIdOrderByEventSeqAsc(runId);
        return events.stream().map(e -> Map.<String, Object>of(
                "seq", e.getEventSeq(),
                "level", e.getLevel() != null ? e.getLevel() : "INFO",
                "message", e.getMessage() != null ? e.getMessage() : "",
                "timestamp", e.getTimestamp()
        )).collect(Collectors.toList());
    }
}
