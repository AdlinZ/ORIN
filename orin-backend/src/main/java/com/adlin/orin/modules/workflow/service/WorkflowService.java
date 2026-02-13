package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.modules.workflow.dto.WorkflowRequest;
import com.adlin.orin.modules.workflow.dto.WorkflowResponse;
import com.adlin.orin.modules.workflow.dto.WorkflowStepRequest;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowStepEntity;
import com.adlin.orin.modules.workflow.engine.WorkflowEngine;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowStepRepository;
import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepRepository stepRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowEngine workflowEngine;
    private final DifyDslConverter difyDslConverter;

    @Transactional
    public WorkflowResponse importDifyWorkflow(String name, String description, String yamlContent) {
        log.info("Importing Dify workflow: {}", name);

        Map<String, Object> workflowDefinition = difyDslConverter.convert(yamlContent);

        WorkflowEntity entity = WorkflowEntity.builder()
                .workflowName(name)
                .description(description)
                .workflowType(WorkflowEntity.WorkflowType.DAG)
                .workflowDefinition(workflowDefinition)
                .status(WorkflowEntity.WorkflowStatus.DRAFT) // Import as draft
                .build();

        WorkflowEntity saved = workflowRepository.save(entity);
        log.info("Dify workflow imported with ID: {}", saved.getId());

        return WorkflowResponse.fromEntity(saved);
    }

    @Transactional
    public WorkflowResponse createWorkflow(WorkflowRequest request) {
        log.info("Creating workflow: {}", request.getWorkflowName());

        // Check if this is an update (has ID) or create (no ID)
        if (request.getId() != null) {
            return updateWorkflow(request.getId(), request);
        }

        if (workflowRepository.existsByWorkflowName(request.getWorkflowName())) {
            throw new IllegalArgumentException("Workflow name already exists: " + request.getWorkflowName());
        }

        WorkflowEntity entity = WorkflowEntity.builder()
                .workflowName(request.getWorkflowName())
                .description(request.getDescription())
                .workflowType(request.getWorkflowType())
                .workflowDefinition(
                        request.getWorkflowDefinition() != null ? request.getWorkflowDefinition() : new HashMap<>())
                .timeoutSeconds(request.getTimeoutSeconds())
                .retryPolicy(request.getRetryPolicy())
                .createdBy(request.getCreatedBy())
                .build();

        WorkflowEntity saved = workflowRepository.save(entity);
        log.info("Workflow created with ID: {}", saved.getId());

        // Create steps if provided
        if (request.getSteps() != null && !request.getSteps().isEmpty()) {
            for (WorkflowStepRequest stepRequest : request.getSteps()) {
                addStep(saved.getId(), stepRequest);
            }
        }

        return WorkflowResponse.fromEntity(saved);
    }

    @Transactional
    public WorkflowResponse updateWorkflow(Long id, WorkflowRequest request) {
        log.info("Updating workflow: {}", id);

        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));

        // Update fields
        if (request.getWorkflowName() != null) {
            entity.setWorkflowName(request.getWorkflowName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getWorkflowType() != null) {
            entity.setWorkflowType(request.getWorkflowType());
        }
        if (request.getWorkflowDefinition() != null) {
            entity.setWorkflowDefinition(request.getWorkflowDefinition());
        }
        if (request.getTimeoutSeconds() != null) {
            entity.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        if (request.getRetryPolicy() != null) {
            entity.setRetryPolicy(request.getRetryPolicy());
        }

        WorkflowEntity saved = workflowRepository.save(entity);
        log.info("Workflow updated with ID: {}", saved.getId());

        return WorkflowResponse.fromEntity(saved);
    }

    @Transactional
    public void addStep(Long workflowId, WorkflowStepRequest request) {
        log.info("Adding step to workflow: {}", workflowId);

        if (!workflowRepository.existsById(workflowId)) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }

        WorkflowStepEntity.StepType type = WorkflowStepEntity.StepType.SKILL;
        if (request.getStepType() != null) {
            try {
                type = WorkflowStepEntity.StepType.valueOf(request.getStepType());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid step type: {}, defaulting to SKILL", request.getStepType());
            }
        }

        WorkflowStepEntity step = WorkflowStepEntity.builder()
                .workflowId(workflowId)
                .stepOrder(request.getStepOrder())
                .stepName(request.getStepName())
                .stepType(type)
                .skillId(request.getSkillId())
                .agentId(request.getAgentId())
                .inputMapping(request.getInputMapping())
                .outputMapping(request.getOutputMapping())
                .conditionExpression(request.getConditionExpression())
                .dependsOn(request.getDependsOn())
                .build();

        stepRepository.save(step);
        log.info("Step added to workflow: {}", workflowId);
    }

    public Long triggerWorkflow(Long workflowId, Map<String, Object> inputs, String triggeredBy) {
        log.info("Triggering workflow: {}", workflowId);

        // 验证工作流
        if (!workflowEngine.validateWorkflow(workflowId)) {
            throw new IllegalStateException("Workflow validation failed: " + workflowId);
        }

        // 创建实例
        WorkflowInstanceEntity instance = workflowEngine.createInstance(workflowId, inputs, triggeredBy);

        // 异步执行
        workflowEngine.executeInstanceAsync(instance.getId());

        return instance.getId();
    }

    public List<WorkflowResponse> getAllWorkflows() {
        return workflowRepository.findAll().stream()
                .map(WorkflowResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public WorkflowResponse getWorkflowById(Long id) {
        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        return WorkflowResponse.fromEntity(entity);
    }

    public List<WorkflowInstanceEntity> getWorkflowInstances(Long workflowId) {
        return instanceRepository.findByWorkflowIdOrderByStartedAtDesc(workflowId);
    }

    @Transactional
    public void deleteWorkflow(Long id) {
        log.info("Deleting workflow: {}", id);
        if (!workflowRepository.existsById(id)) {
            throw new IllegalArgumentException("Workflow not found: " + id);
        }

        // Delete associated steps and instances
        stepRepository.deleteByWorkflowId(id);
        instanceRepository.deleteByWorkflowId(id);

        // Delete the workflow itself
        workflowRepository.deleteById(id);
        log.info("Workflow deleted: {}", id);
    }

    public com.adlin.orin.modules.workflow.dto.WorkflowAccessResponse getWorkflowAccessInfo(Long id) {
        // Validate existence
        if (!workflowRepository.existsById(id)) {
            throw new IllegalArgumentException("Workflow not found: " + id);
        }

        // For now, construct simplified access info
        // In a production environment, this should fetch dynamic config (hosts, ports)
        String baseUrl = "http://localhost:8080";

        return com.adlin.orin.modules.workflow.dto.WorkflowAccessResponse.builder()
                .webAppUrl("http://localhost:5173/chat/" + id) // Frontend App URL
                .apiUrl(baseUrl + "/v1/workflows/" + id + "/execute")
                .apiKey("sk-orin-" + java.util.UUID.randomUUID().toString()) // Placeholder API Key
                .build();
    }
}
