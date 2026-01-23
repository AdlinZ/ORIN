package com.adlin.orin.modules.agent.mapper;

import com.adlin.orin.modules.agent.dto.AgentCreateRequest;
import com.adlin.orin.modules.agent.dto.AgentListResponse;
import com.adlin.orin.modules.agent.dto.AgentResponse;
import com.adlin.orin.modules.agent.dto.AgentUpdateRequest;
import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import org.mapstruct.*;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Agent实体与DTO转换Mapper
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface AgentMapper {

    /**
     * Entity转Response DTO
     */
    @Mapping(target = "agentId", source = "metadata.agentId")
    @Mapping(target = "name", source = "metadata.name")
    @Mapping(target = "description", source = "metadata.description")
    @Mapping(target = "modelName", source = "metadata.modelName")
    @Mapping(target = "providerType", source = "metadata.providerType")
    @Mapping(target = "syncTime", source = "metadata.syncTime")

    @Mapping(target = "endpointDomain", source = "profile.endpointUrl", qualifiedByName = "extractDomain")
    @Mapping(target = "connectionStatus", source = "profile.connectionStatus")
    @Mapping(target = "createdAt", source = "profile.createdAt")
    @Mapping(target = "updatedAt", source = "profile.updatedAt")

    @Mapping(target = "health", source = "healthStatus")
    AgentResponse toResponse(AgentMetadata metadata,
            AgentAccessProfile profile,
            AgentHealthStatus healthStatus);

    /**
     * 简化版：仅从Metadata转换
     */
    @Mapping(target = "endpointDomain", ignore = true)
    @Mapping(target = "connectionStatus", ignore = true)
    @Mapping(target = "health", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AgentResponse toResponse(AgentMetadata metadata);

    /**
     * 健康状态转换
     */
    @Mapping(target = "status", expression = "java(healthStatus.getStatus() != null ? healthStatus.getStatus().name() : \"UNKNOWN\")")
    @Mapping(target = "responseTimeMs", constant = "0")
    @Mapping(target = "lastCheckTime", expression = "java(mapTimestamp(healthStatus.getLastHeartbeat()))")
    AgentResponse.HealthStatus toHealthStatusDto(AgentHealthStatus healthStatus);

    /**
     * CreateRequest转Metadata Entity
     */
    @Mapping(target = "agentId", ignore = true)
    @Mapping(target = "syncTime", ignore = true)
    @Mapping(target = "icon", ignore = true)
    @Mapping(target = "mode", ignore = true)
    @Mapping(target = "temperature", ignore = true)
    @Mapping(target = "topP", ignore = true)
    @Mapping(target = "maxTokens", ignore = true)
    @Mapping(target = "systemPrompt", ignore = true)
    AgentMetadata toMetadata(AgentCreateRequest request);

    /**
     * CreateRequest转AccessProfile Entity
     */
    @Mapping(target = "agentId", ignore = true)
    @Mapping(target = "connectionStatus", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AgentAccessProfile toAccessProfile(AgentCreateRequest request);

    /**
     * UpdateRequest更新Metadata Entity
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "agentId", ignore = true)
    @Mapping(target = "syncTime", ignore = true)
    @Mapping(target = "providerType", ignore = true)
    @Mapping(target = "icon", ignore = true)
    @Mapping(target = "mode", ignore = true)
    @Mapping(target = "temperature", ignore = true)
    @Mapping(target = "topP", ignore = true)
    @Mapping(target = "maxTokens", ignore = true)
    @Mapping(target = "systemPrompt", ignore = true)
    @Mapping(target = "modelName", ignore = true)
    void updateMetadataFromRequest(AgentUpdateRequest request, @MappingTarget AgentMetadata metadata);

    /**
     * UpdateRequest更新AccessProfile Entity
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "agentId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateAccessProfileFromRequest(AgentUpdateRequest request, @MappingTarget AgentAccessProfile profile);

    /**
     * Entity列表转Summary列表
     */
    List<AgentListResponse.AgentSummary> toSummaryList(List<AgentMetadata> metadataList);

    /**
     * Entity转Summary
     */
    @Mapping(target = "healthStatus", ignore = true)
    @Mapping(target = "connectionStatus", ignore = true)
    AgentListResponse.AgentSummary toSummary(AgentMetadata metadata);

    /**
     * 从URL提取域名
     */
    @Named("extractDomain")
    default String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (Exception e) {
            return "Invalid URL";
        }
    }

    default LocalDateTime mapTimestamp(Long timestamp) {
        if (timestamp == null)
            return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }
}
