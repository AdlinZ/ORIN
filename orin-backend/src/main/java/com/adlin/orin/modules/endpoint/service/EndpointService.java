package com.adlin.orin.modules.endpoint.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.endpoint.dto.EndpointResponse;
import com.adlin.orin.modules.endpoint.dto.PublishEndpointRequest;
import com.adlin.orin.modules.endpoint.entity.AgentEndpoint;
import com.adlin.orin.modules.endpoint.entity.EndpointStatus;
import com.adlin.orin.modules.endpoint.entity.EndpointType;
import com.adlin.orin.modules.endpoint.repository.AgentEndpointRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint 核心服务（F05）— 将已冻结 AgentVersion 发布为 API / MCP 端点。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EndpointService {

    private final AgentEndpointRepository endpointRepository;
    private final AgentMetadataRepository agentMetadataRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final ObjectMapper objectMapper;
    private final EndpointOwnershipResolver ownershipResolver;
    private final AgentOwnershipResolver agentOwnershipResolver;
    private final GatewaySecretRepository gatewaySecretRepository;
    private final GatewaySecretService gatewaySecretService;

    /**
     * 发布端点：将已冻结的 AgentVersion 发布为 REST API 或 MCP Server。
     */
    @Transactional
    public EndpointResponse publish(PublishEndpointRequest request, String createdBy) {
        // 1. 校验 Agent
        AgentMetadata agent = agentMetadataRepository.findById(request.getAgentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        agentOwnershipResolver.assertCanManage(agent);

        // 2. 校验版本已冻结
        AgentVersion version = agentVersionRepository.findById(request.getAgentVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_VERSION_NOT_FROZEN));
        if (version.getStatus() != AgentVersion.Status.FROZEN) {
            throw new BusinessException(ErrorCode.ENDPOINT_VERSION_NOT_FROZEN);
        }
        if (!version.getAgentId().equals(request.getAgentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "AgentVersion 不属于该 Agent");
        }

        // 3. 类型
        EndpointType type = EndpointType.REST_API;
        if ("MCP_SERVER".equalsIgnoreCase(request.getEndpointType())) {
            type = EndpointType.MCP_SERVER;
        }

        // 4. 生成唯一路径
        String id = "ep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String path = "/api/endpoints/" + id;

        // 5. 自动创建 API Key 并写入 config.allowedApiKeyIds
        String apiKeyName = (request.getName() != null ? request.getName() : "endpoint") + "-key";
        GatewaySecretService.ClientAccessSecretWithValue keyResult =
                gatewaySecretService.createClientAccessSecret(
                        createdBy, apiKeyName, "Auto-created for endpoint " + id,
                        100, 10000, 1_000_000L, null, createdBy);
        String configJson;
        try {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("allowedApiKeyIds", new ArrayList<>(List.of(keyResult.getSecret().getSecretId())));
            configJson = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED,
                    "Failed to serialize endpoint config", e);
        }

        // 6. 创建
        AgentEndpoint ep = AgentEndpoint.builder()
                .id(id)
                .agentId(agent.getAgentId())
                .agentVersionId(version.getId())
                .name(request.getName())
                .endpointType(type)
                .status(EndpointStatus.ACTIVE)
                .endpointPath(path)
                .config(configJson)
                .description(request.getDescription())
                .createdBy(createdBy)
                .build();

        ep = endpointRepository.save(ep);
        log.info("Endpoint published: {} {} type={} path={} keyId={}",
                ep.getId(), ep.getName(), type, path, keyResult.getSecret().getSecretId());

        // 7. 返回（包含一次性 API Key 明文）
        EndpointResponse resp = EndpointResponse.from(ep);
        resp.setSecretKey(keyResult.getSecretValue());
        return resp;
    }

    /**
     * 下线端点（软删除：ACTIVE → INACTIVE）。
     */
    @Transactional
    public EndpointResponse deactivate(String endpointId, String operator) {
        AgentEndpoint ep = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND));
        ownershipResolver.assertCanManage(ep);
        if (ep.getStatus() != EndpointStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED,
                    "Endpoint 状态不是 ACTIVE: " + ep.getStatus());
        }
        ep.setStatus(EndpointStatus.INACTIVE);
        ep = endpointRepository.save(ep);
        log.info("Endpoint deactivated: {} by {}", endpointId, operator);
        return EndpointResponse.from(ep);
    }

    /**
     * 重新激活端点。
     */
    @Transactional
    public EndpointResponse activate(String endpointId, String operator) {
        AgentEndpoint ep = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND));
        ownershipResolver.assertCanManage(ep);
        if (ep.getStatus() != EndpointStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED,
                    "Endpoint 状态不是 INACTIVE: " + ep.getStatus());
        }
        ep.setStatus(EndpointStatus.ACTIVE);
        ep = endpointRepository.save(ep);
        log.info("Endpoint activated: {} by {}", endpointId, operator);
        return EndpointResponse.from(ep);
    }

    // ---- 查询 ----

    @Transactional(readOnly = true)
    public EndpointResponse getEndpoint(String endpointId) {
        AgentEndpoint ep = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND));
        ownershipResolver.assertCanManage(ep);
        return EndpointResponse.from(ep);
    }

    @Transactional(readOnly = true)
    public Page<EndpointResponse> listEndpoints(Pageable pageable) {
        if (!ownershipResolver.isCurrentUserPrivileged()) {
            return endpointRepository
                    .findByCreatedByOrderByCreatedAtDesc(ownershipResolver.currentOwnerId(), pageable)
                    .map(EndpointResponse::from);
        }
        return endpointRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(EndpointResponse::from);
    }

    // ---- F05 访问控制 ----

    /**
     * 为 Endpoint 分配 API Key 访问权限。
     *
     * <p>将 apiKeyId 追加到 endpoint.config.allowedApiKeyIds。
     */
    @Transactional
    public EndpointResponse assignApiKey(String endpointId, String apiKeyId) {
        AgentEndpoint ep = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND));
        ownershipResolver.assertCanManage(ep);

        GatewaySecret key = gatewaySecretRepository.findBySecretId(apiKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "API Key 不存在或无权使用"));
        if (!key.isClientAccess()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅允许分配 CLIENT_ACCESS API Key");
        }
        AgentMetadata agent = agentMetadataRepository.findById(ep.getAgentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));
        if (agent.getOwnerUserId() == null || key.getUserId() == null
                || !String.valueOf(agent.getOwnerUserId()).equals(key.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent 与 API Key 不属于同一 owner");
        }

        Map<String, Object> config = parseConfig(ep.getConfig());
        @SuppressWarnings("unchecked")
        List<String> allowed = (List<String>) config.computeIfAbsent("allowedApiKeyIds",
                k -> new ArrayList<>());
        if (!allowed.contains(apiKeyId)) {
            allowed.add(apiKeyId);
        }
        try {
            ep.setConfig(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED,
                    "Failed to serialize endpoint config", e);
        }
        ep = endpointRepository.save(ep);
        log.info("API key {} assigned to endpoint {}", apiKeyId, endpointId);
        return EndpointResponse.from(ep);
    }

    /**
     * 撤销 API Key 对 Endpoint 的访问权限。
     */
    @Transactional
    public EndpointResponse revokeApiKey(String endpointId, String apiKeyId) {
        AgentEndpoint ep = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND));
        ownershipResolver.assertCanManage(ep);
        Map<String, Object> config = parseConfig(ep.getConfig());
        @SuppressWarnings("unchecked")
        List<String> allowed = (List<String>) config.get("allowedApiKeyIds");
        if (allowed != null) {
            allowed.remove(apiKeyId);
        }
        try {
            ep.setConfig(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED,
                    "Failed to serialize endpoint config", e);
        }
        ep = endpointRepository.save(ep);
        log.info("API key {} revoked from endpoint {}", apiKeyId, endpointId);
        return EndpointResponse.from(ep);
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(configJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
