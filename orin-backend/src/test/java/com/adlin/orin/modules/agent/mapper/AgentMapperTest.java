package com.adlin.orin.modules.agent.mapper;

import com.adlin.orin.modules.agent.dto.AgentCreateRequest;
import com.adlin.orin.modules.agent.dto.AgentResponse;
import com.adlin.orin.modules.agent.dto.AgentUpdateRequest;
import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentMapper 单元测试
 * 使用 MapStruct 的 Mappers.getMapper() 进行纯单元测试，不依赖 Spring 上下文
 */
class AgentMapperTest {

    private AgentMapper agentMapper;

    @BeforeEach
    void setUp() {
        // 使用 MapStruct 的工厂方法获取 Mapper 实例
        agentMapper = Mappers.getMapper(AgentMapper.class);
    }

    @Test
    void testToResponse_WithFullData() {
        // Given
        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId("test-agent-001");
        metadata.setName("Test Agent");
        metadata.setDescription("Test Description");
        metadata.setModelName("gpt-4");
        metadata.setProviderType("OPENAI");
        metadata.setSyncTime(LocalDateTime.now());

        AgentAccessProfile profile = new AgentAccessProfile();
        profile.setAgentId("test-agent-001");
        profile.setEndpointUrl("https://api.example.com");
        profile.setConnectionStatus("ACTIVE");
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());

        AgentHealthStatus healthStatus = new AgentHealthStatus();
        healthStatus.setAgentId("test-agent-001");
        healthStatus.setStatus(AgentHealthStatus.Status.RUNNING);
        healthStatus.setLastHeartbeat(System.currentTimeMillis());

        // When
        AgentResponse response = agentMapper.toResponse(metadata, profile, healthStatus);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAgentId()).isEqualTo("test-agent-001");
        assertThat(response.getName()).isEqualTo("Test Agent");
        assertThat(response.getDescription()).isEqualTo("Test Description");
        assertThat(response.getModelName()).isEqualTo("gpt-4");
        assertThat(response.getProviderType()).isEqualTo("OPENAI");
        assertThat(response.getConnectionStatus()).isEqualTo("ACTIVE");
        assertThat(response.getHealth()).isNotNull();
    }

    @Test
    void testToResponse_WithMetadataOnly() {
        // Given
        AgentMetadata metadata = new AgentMetadata();
        metadata.setAgentId("test-agent-002");
        metadata.setName("Minimal Agent");

        // When
        AgentResponse response = agentMapper.toResponse(metadata);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAgentId()).isEqualTo("test-agent-002");
        assertThat(response.getName()).isEqualTo("Minimal Agent");
    }

    @Test
    void testToMetadata_FromCreateRequest() {
        // Given
        AgentCreateRequest request = AgentCreateRequest.builder()
                .name("New Agent")
                .description("New Description")
                .endpointUrl("https://api.new.com")
                .apiKey("test-key")
                .providerType("OPENAI")
                .modelName("gpt-3.5-turbo")
                .build();

        // When
        AgentMetadata metadata = agentMapper.toMetadata(request);

        // Then
        assertThat(metadata).isNotNull();
        assertThat(metadata.getName()).isEqualTo("New Agent");
        assertThat(metadata.getDescription()).isEqualTo("New Description");
        assertThat(metadata.getProviderType()).isEqualTo("OPENAI");
        assertThat(metadata.getModelName()).isEqualTo("gpt-3.5-turbo");
    }

    @Test
    void testToAccessProfile_FromCreateRequest() {
        // Given
        AgentCreateRequest request = AgentCreateRequest.builder()
                .endpointUrl("https://api.test.com")
                .apiKey("secret-key")
                .build();

        // When
        AgentAccessProfile profile = agentMapper.toAccessProfile(request);

        // Then
        assertThat(profile).isNotNull();
        assertThat(profile.getEndpointUrl()).isEqualTo("https://api.test.com");
        assertThat(profile.getApiKey()).isEqualTo("secret-key");
        assertThat(profile.getConnectionStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void testUpdateMetadataFromRequest() {
        // Given
        AgentMetadata existing = new AgentMetadata();
        existing.setAgentId("test-agent-003");
        existing.setName("Old Name");
        existing.setDescription("Old Description");

        AgentUpdateRequest request = AgentUpdateRequest.builder()
                .name("Updated Name")
                .description("Updated Description")
                .build();

        // When
        agentMapper.updateMetadataFromRequest(request, existing);

        // Then
        assertThat(existing.getName()).isEqualTo("Updated Name");
        assertThat(existing.getDescription()).isEqualTo("Updated Description");
        assertThat(existing.getAgentId()).isEqualTo("test-agent-003"); // Should not change
    }

    @Test
    void testUpdateMetadataFromRequest_NullValues() {
        // Given
        AgentMetadata existing = new AgentMetadata();
        existing.setName("Original Name");
        existing.setDescription("Original Description");

        AgentUpdateRequest request = AgentUpdateRequest.builder()
                .name("New Name")
                // description is null
                .build();

        // When
        agentMapper.updateMetadataFromRequest(request, existing);

        // Then
        assertThat(existing.getName()).isEqualTo("New Name");
        assertThat(existing.getDescription()).isEqualTo("Original Description"); // Should not change
    }

    @Test
    void testExtractDomain() {
        // Given
        String url = "https://api.example.com/v1/chat";

        // When
        String domain = agentMapper.extractDomain(url);

        // Then
        assertThat(domain).isEqualTo("api.example.com");
    }

    @Test
    void testExtractDomain_InvalidUrl() {
        // Given
        String invalidUrl = "not-a-valid-url";

        // When
        String domain = agentMapper.extractDomain(invalidUrl);

        // Then
        assertThat(domain).isEqualTo("Invalid URL");
    }

    @Test
    void testExtractDomain_NullUrl() {
        // When
        String domain = agentMapper.extractDomain(null);

        // Then
        assertThat(domain).isNull();
    }
}
