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

    @Transactional
    public WorkflowResponse createWorkflow(WorkflowRequest request) {
        log.info("Creating workflow: {}", request.getWorkflowName());

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

        return WorkflowResponse.fromEntity(saved);
    }

    @Transactional
    public void addStep(Long workflowId, WorkflowStepRequest request) {
        log.info("Adding step to workflow: {}", workflowId);

        if (!workflowRepository.existsById(workflowId)) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }

        WorkflowStepEntity step = WorkflowStepEntity.builder()
                .workflowId(workflowId)
                .stepOrder(request.getStepOrder())
                .stepName(request.getStepName())
                .skillId(request.getSkillId())
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

        // 执行工作流
        return workflowEngine.executeWorkflow(workflowId, inputs, triggeredBy);
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
}
