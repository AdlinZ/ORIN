package com.adlin.orin.modules.endpoint.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.endpoint.dto.EndpointResponse;
import com.adlin.orin.modules.endpoint.dto.PublishEndpointRequest;
import com.adlin.orin.modules.endpoint.entity.AgentEndpoint;
import com.adlin.orin.modules.endpoint.entity.EndpointStatus;
import com.adlin.orin.modules.endpoint.entity.EndpointType;
import com.adlin.orin.modules.endpoint.repository.AgentEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 发布端点：将已冻结的 AgentVersion 发布为 REST API 或 MCP Server。
     */
    @Transactional
    public EndpointResponse publish(PublishEndpointRequest request, String createdBy) {
        // 1. 校验 Agent
        AgentMetadata agent = agentMetadataRepository.findById(request.getAgentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND));

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

        // 5. 创建
        AgentEndpoint ep = AgentEndpoint.builder()
                .id(id)
                .agentId(agent.getAgentId())
                .agentVersionId(version.getId())
                .name(request.getName())
                .endpointType(type)
                .status(EndpointStatus.ACTIVE)
                .endpointPath(path)
                .description(request.getDescription())
                .createdBy(createdBy)
                .build();

        ep = endpointRepository.save(ep);
        log.info("Endpoint published: {} {} type={} path={}", ep.getId(), ep.getName(), type, path);
        return EndpointResponse.from(ep);
    }

    /**
     * 下线端点（软删除：ACTIVE → INACTIVE）。
     */
    @Transactional
    public EndpointResponse deactivate(String endpointId, String operator) {
        AgentEndpoint ep = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND));
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
        return EndpointResponse.from(ep);
    }

    @Transactional(readOnly = true)
    public Page<EndpointResponse> listEndpoints(Pageable pageable) {
        return endpointRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(EndpointResponse::from);
    }
}
