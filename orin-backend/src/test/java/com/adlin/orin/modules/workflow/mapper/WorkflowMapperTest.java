package com.adlin.orin.modules.workflow.mapper;

import com.adlin.orin.modules.workflow.dto.WorkflowRequest;
import com.adlin.orin.modules.workflow.dto.WorkflowResponse;
import com.adlin.orin.modules.workflow.dto.WorkflowStepRequest;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowStepEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowMapper 单元测试
 */
class WorkflowMapperTest {

    private WorkflowMapper workflowMapper;

    @BeforeEach
    void setUp() {
        workflowMapper = Mappers.getMapper(WorkflowMapper.class);
    }

    @Test
    void testToResponse() {
        // Given
        Map<String, Object> definition = new HashMap<>();
        definition.put("type", "sequential");

        WorkflowEntity entity = WorkflowEntity.builder()
                .id(1L)
                .workflowName("Test Workflow")
                .description("Test Description")
                .workflowType(WorkflowEntity.WorkflowType.SEQUENTIAL)
                .workflowDefinition(definition)
                .timeoutSeconds(300)
                .status(WorkflowEntity.WorkflowStatus.ACTIVE)
                .version("1.0.0")
                .build();

        // When
        WorkflowResponse response = workflowMapper.toResponse(entity);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getWorkflowName()).isEqualTo("Test Workflow");
        assertThat(response.getDescription()).isEqualTo("Test Description");
        assertThat(response.getWorkflowType()).isEqualTo(WorkflowEntity.WorkflowType.SEQUENTIAL);
        assertThat(response.getStatus()).isEqualTo(WorkflowEntity.WorkflowStatus.ACTIVE);
        assertThat(response.getTimeoutSeconds()).isEqualTo(300);
    }

    @Test
    void testToEntity() {
        // Given
        Map<String, Object> definition = new HashMap<>();
        definition.put("steps", "[]");

        WorkflowRequest request = WorkflowRequest.builder()
                .workflowName("New Workflow")
                .description("New Description")
                .workflowType(WorkflowEntity.WorkflowType.PARALLEL)
                .workflowDefinition(definition)
                .timeoutSeconds(600)
                .build();

        // When
        WorkflowEntity entity = workflowMapper.toEntity(request);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getWorkflowName()).isEqualTo("New Workflow");
        assertThat(entity.getDescription()).isEqualTo("New Description");
        assertThat(entity.getWorkflowType()).isEqualTo(WorkflowEntity.WorkflowType.PARALLEL);
        assertThat(entity.getStatus()).isEqualTo(WorkflowEntity.WorkflowStatus.DRAFT);
        assertThat(entity.getTimeoutSeconds()).isEqualTo(600);
    }

    @Test
    void testUpdateEntityFromRequest() {
        // Given
        WorkflowEntity existing = WorkflowEntity.builder()
                .id(1L)
                .workflowName("Old Name")
                .description("Old Description")
                .status(WorkflowEntity.WorkflowStatus.ACTIVE)
                .build();

        WorkflowRequest request = WorkflowRequest.builder()
                .workflowName("Updated Name")
                .description("Updated Description")
                .build();

        // When
        workflowMapper.updateEntityFromRequest(request, existing);

        // Then
        assertThat(existing.getWorkflowName()).isEqualTo("Updated Name");
        assertThat(existing.getDescription()).isEqualTo("Updated Description");
        assertThat(existing.getId()).isEqualTo(1L); // Should not change
        assertThat(existing.getStatus()).isEqualTo(WorkflowEntity.WorkflowStatus.ACTIVE); // Should not change
    }

    @Test
    void testStepRequestToEntity() {
        // Given
        Map<String, Object> inputMapping = new HashMap<>();
        inputMapping.put("input", "value");

        WorkflowStepRequest request = WorkflowStepRequest.builder()
                .stepOrder(1)
                .stepName("Test Step")
                .skillId(100L)
                .inputMapping(inputMapping)
                .build();

        // When
        WorkflowStepEntity entity = workflowMapper.stepRequestToEntity(request);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getStepOrder()).isEqualTo(1);
        assertThat(entity.getStepName()).isEqualTo("Test Step");
        assertThat(entity.getSkillId()).isEqualTo(100L);
        assertThat(entity.getInputMapping()).isEqualTo(inputMapping);
    }
}
