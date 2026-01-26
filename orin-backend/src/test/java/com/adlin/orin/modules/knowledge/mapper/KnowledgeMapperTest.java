package com.adlin.orin.modules.knowledge.mapper;

import com.adlin.orin.modules.knowledge.dto.KnowledgeBaseCreateRequest;
import com.adlin.orin.modules.knowledge.dto.KnowledgeBaseResponse;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeMapper 单元测试
 */
class KnowledgeMapperTest {

    private KnowledgeMapper knowledgeMapper;

    @BeforeEach
    void setUp() {
        knowledgeMapper = Mappers.getMapper(KnowledgeMapper.class);
    }

    @Test
    void testToResponse() {
        // Given
        KnowledgeBase entity = new KnowledgeBase();
        entity.setId("kb-001");
        entity.setName("Test KB");
        entity.setDescription("Test Description");
        entity.setSourceAgentId("agent-001");
        entity.setType(KnowledgeType.UNSTRUCTURED);
        entity.setStatus("ENABLED");
        entity.setDocCount(10);
        entity.setTotalSizeMb(5.5);

        // When
        KnowledgeBaseResponse response = knowledgeMapper.toResponse(entity);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getKbId()).isEqualTo("kb-001");
        assertThat(response.getName()).isEqualTo("Test KB");
        assertThat(response.getDescription()).isEqualTo("Test Description");
        assertThat(response.getAgentId()).isEqualTo("agent-001");
        assertThat(response.getEnabled()).isTrue();
        assertThat(response.getDocumentCount()).isEqualTo(10);
    }

    @Test
    void testToResponse_DisabledStatus() {
        // Given
        KnowledgeBase entity = new KnowledgeBase();
        entity.setId("kb-002");
        entity.setName("Disabled KB");
        entity.setStatus("DISABLED");

        // When
        KnowledgeBaseResponse response = knowledgeMapper.toResponse(entity);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getEnabled()).isFalse();
    }

    @Test
    void testToEntity() {
        // Given
        KnowledgeBaseCreateRequest request = KnowledgeBaseCreateRequest.builder()
                .name("New KB")
                .description("New Description")
                .agentId("agent-002")
                .type("UNSTRUCTURED")
                .enabled(true)
                .build();

        // When
        KnowledgeBase entity = knowledgeMapper.toEntity(request);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getName()).isEqualTo("New KB");
        assertThat(entity.getDescription()).isEqualTo("New Description");
        assertThat(entity.getSourceAgentId()).isEqualTo("agent-002");
        assertThat(entity.getStatus()).isEqualTo("ENABLED");
        assertThat(entity.getDocCount()).isEqualTo(0);
        assertThat(entity.getTotalSizeMb()).isEqualTo(0.0);
    }

    @Test
    void testToEntity_DisabledRequest() {
        // Given
        KnowledgeBaseCreateRequest request = KnowledgeBaseCreateRequest.builder()
                .name("Disabled KB")
                .enabled(false)
                .build();

        // When
        KnowledgeBase entity = knowledgeMapper.toEntity(request);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getStatus()).isEqualTo("DISABLED");
    }
}
