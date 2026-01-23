package com.adlin.orin.modules.skill.mapper;

import com.adlin.orin.modules.skill.dto.SkillRequest;
import com.adlin.orin.modules.skill.dto.SkillResponse;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkillMapper 单元测试
 */
class SkillMapperTest {

    private SkillMapper skillMapper;

    @BeforeEach
    void setUp() {
        skillMapper = Mappers.getMapper(SkillMapper.class);
    }

    @Test
    void testToResponse() {
        // Given
        SkillEntity entity = SkillEntity.builder()
                .id(1L)
                .skillName("Test Skill")
                .skillType(SkillEntity.SkillType.API)
                .description("Test Description")
                .apiEndpoint("https://api.test.com")
                .apiMethod("POST")
                .version("1.0.0")
                .status(SkillEntity.SkillStatus.ACTIVE)
                .build();

        // When
        SkillResponse response = skillMapper.toResponse(entity);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSkillName()).isEqualTo("Test Skill");
        assertThat(response.getSkillType()).isEqualTo(SkillEntity.SkillType.API);
        assertThat(response.getDescription()).isEqualTo("Test Description");
        assertThat(response.getApiEndpoint()).isEqualTo("https://api.test.com");
        assertThat(response.getStatus()).isEqualTo(SkillEntity.SkillStatus.ACTIVE);
    }

    @Test
    void testToEntity() {
        // Given
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        SkillRequest request = SkillRequest.builder()
                .skillName("New Skill")
                .skillType(SkillEntity.SkillType.KNOWLEDGE)
                .description("New Description")
                .knowledgeConfigId(100L)
                .inputSchema(inputSchema)
                .build();

        // When
        SkillEntity entity = skillMapper.toEntity(request);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getSkillName()).isEqualTo("New Skill");
        assertThat(entity.getSkillType()).isEqualTo(SkillEntity.SkillType.KNOWLEDGE);
        assertThat(entity.getDescription()).isEqualTo("New Description");
        assertThat(entity.getKnowledgeConfigId()).isEqualTo(100L);
        assertThat(entity.getStatus()).isEqualTo(SkillEntity.SkillStatus.ACTIVE);
        assertThat(entity.getInputSchema()).isEqualTo(inputSchema);
    }

    @Test
    void testUpdateEntityFromRequest() {
        // Given
        SkillEntity existing = SkillEntity.builder()
                .id(1L)
                .skillName("Old Name")
                .description("Old Description")
                .status(SkillEntity.SkillStatus.ACTIVE)
                .build();

        SkillRequest request = SkillRequest.builder()
                .skillName("Updated Name")
                .description("Updated Description")
                .build();

        // When
        skillMapper.updateEntityFromRequest(request, existing);

        // Then
        assertThat(existing.getSkillName()).isEqualTo("Updated Name");
        assertThat(existing.getDescription()).isEqualTo("Updated Description");
        assertThat(existing.getId()).isEqualTo(1L); // Should not change
        assertThat(existing.getStatus()).isEqualTo(SkillEntity.SkillStatus.ACTIVE); // Should not change
    }

    @Test
    void testUpdateEntityFromRequest_NullValues() {
        // Given
        SkillEntity existing = SkillEntity.builder()
                .skillName("Original Name")
                .description("Original Description")
                .build();

        SkillRequest request = SkillRequest.builder()
                .skillName("New Name")
                // description is null
                .build();

        // When
        skillMapper.updateEntityFromRequest(request, existing);

        // Then
        assertThat(existing.getSkillName()).isEqualTo("New Name");
        assertThat(existing.getDescription()).isEqualTo("Original Description"); // Should not change
    }
}
