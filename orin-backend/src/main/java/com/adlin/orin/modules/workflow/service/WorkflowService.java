package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.service.TaskService;
import com.adlin.orin.modules.workflow.dto.WorkflowRequest;
import com.adlin.orin.modules.workflow.dto.WorkflowExecutionSubmissionResponse;
import com.adlin.orin.modules.workflow.dto.WorkflowResponse;
import com.adlin.orin.modules.workflow.dto.WorkflowStepRequest;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowStepEntity;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslValidator;
import com.adlin.orin.modules.workflow.engine.WorkflowEngine;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowStepRepository;
import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
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
    private final OrinWorkflowDslNormalizer workflowDslNormalizer;
    private final OrinWorkflowDslValidator workflowDslValidator;
    private final TaskService taskService;
    private final WorkflowOwnershipResolver workflowOwnershipResolver;

    /**
     * 将字符串转换为 WorkflowType 枚举
     */
    private WorkflowEntity.WorkflowType parseWorkflowType(String type) {
        if (type == null || type.isBlank()) {
            return WorkflowEntity.WorkflowType.DAG;
        }
        try {
            return WorkflowEntity.WorkflowType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid workflow type: {}, defaulting to DAG", type);
            return WorkflowEntity.WorkflowType.DAG;
        }
    }

    private WorkflowEntity.WorkflowStatus parseWorkflowStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WorkflowEntity.WorkflowStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid workflow status: " + status);
        }
    }

    @Transactional
    public WorkflowResponse importDifyWorkflow(String name, String description, String yamlContent) {
        log.info("Importing Dify workflow: {}", name);

        Map<String, Object> workflowDefinition = difyDslConverter.convert(yamlContent);

        WorkflowEntity entity = WorkflowEntity.builder()
                .workflowName(name)
                .description(description)
                .ownerUserId(workflowOwnershipResolver.resolveFromCurrentRequest())
                .workflowType(WorkflowEntity.WorkflowType.DAG)
                .workflowDefinition(workflowDefinition)
                .status(WorkflowEntity.WorkflowStatus.DRAFT) // Import as draft
                .build();

        WorkflowEntity saved = workflowRepository.save(entity);
        log.info("Dify workflow imported with ID: {}", saved.getId());

        return toResponse(saved);
    }

    @Transactional
    public WorkflowResponse createWorkflow(WorkflowRequest request) {
        log.info("Creating workflow: {}", request.getWorkflowName());

        // Check if this is an update (has ID) or create (no ID)
        if (request.getId() != null) {
            return updateWorkflow(request.getId(), request);
        }

        String finalName = request.getWorkflowName();
        if (finalName == null || finalName.trim().isEmpty()) {
            finalName = "未命名工作流";
        }

        String baseName = finalName;
        int counter = 1;
        while (workflowRepository.existsByWorkflowName(finalName)) {
            finalName = baseName + " (" + counter + ")";
            counter++;
        }

        WorkflowEntity.WorkflowStatus requestedStatus = parseWorkflowStatus(request.getStatus());
        if (requestedStatus == WorkflowEntity.WorkflowStatus.ACTIVE) {
            throw new IllegalArgumentException("Use publish endpoint to activate workflow");
        }
        WorkflowEntity entity = WorkflowEntity.builder()
                .workflowName(finalName)
                .description(request.getDescription())
                .ownerUserId(workflowOwnershipResolver.resolveFromCurrentRequest())
                .mcpExposed(Boolean.TRUE.equals(request.getMcpExposed()))
                .workflowType(parseWorkflowType(request.getWorkflowType()))
                .workflowDefinition(workflowDslNormalizer.normalize(request.getWorkflowDefinition(), "ORIN"))
                .timeoutSeconds(request.getTimeoutSeconds())
                .retryPolicy(request.getRetryPolicy())
                .status(requestedStatus != null ? requestedStatus : WorkflowEntity.WorkflowStatus.DRAFT)
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

        return toResponse(saved);
    }

    @Transactional
    public WorkflowResponse updateWorkflow(Long id, WorkflowRequest request) {
        log.info("Updating workflow: {}", id);

        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        // 资源级 ACL 第 3 刀: 非 owner / 非 admin/operator 拒绝更新
        workflowOwnershipResolver.assertCanManage(entity);

        // Update fields
        if (request.getWorkflowName() != null) {
            entity.setWorkflowName(request.getWorkflowName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getWorkflowType() != null) {
            entity.setWorkflowType(parseWorkflowType(request.getWorkflowType()));
        }
        if (request.getWorkflowDefinition() != null) {
            entity.setWorkflowDefinition(workflowDslNormalizer.normalize(request.getWorkflowDefinition(), "ORIN"));
        }
        if (request.getTimeoutSeconds() != null) {
            entity.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        if (request.getRetryPolicy() != null) {
            entity.setRetryPolicy(request.getRetryPolicy());
        }
        if (request.getMcpExposed() != null && request.getMcpExposed() != entity.isMcpExposed()) {
            workflowOwnershipResolver.assertCanManageMcpExposure(entity);
            entity.setMcpExposed(request.getMcpExposed());
        }
        WorkflowEntity.WorkflowStatus requestedStatus = parseWorkflowStatus(request.getStatus());
        if (requestedStatus != null) {
            if (requestedStatus == WorkflowEntity.WorkflowStatus.ACTIVE) {
                ensureWorkflowCanPublish(entity);
            }
            entity.setStatus(requestedStatus);
        }

        WorkflowEntity saved = workflowRepository.save(entity);
        log.info("Workflow updated with ID: {}", saved.getId());

        return toResponse(saved);
    }

    @Transactional
    public WorkflowResponse publishWorkflow(Long id) {
        log.info("Publishing workflow: {}", id);
        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        // 资源级 ACL 第 3 刀: 非 owner / 非 admin/operator 拒绝发布
        workflowOwnershipResolver.assertCanManage(entity);
        ensureWorkflowCanPublish(entity);
        entity.setStatus(WorkflowEntity.WorkflowStatus.ACTIVE);
        return toResponse(workflowRepository.save(entity));
    }

    @Transactional
    public WorkflowResponse archiveWorkflow(Long id) {
        log.info("Archiving workflow: {}", id);
        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        // 资源级 ACL 第 3 刀: 非 owner / 非 admin/operator 拒绝归档
        workflowOwnershipResolver.assertCanManage(entity);
        entity.setStatus(WorkflowEntity.WorkflowStatus.ARCHIVED);
        return toResponse(workflowRepository.save(entity));
    }

    public Map<String, Object> getWorkflowCapabilities() {
        return Map.of(
                "supportedNodeTypes", workflowEngine.getSupportedNodeTypes(),
                "publishRequiredStatus", WorkflowEntity.WorkflowStatus.ACTIVE.name());
    }

    private void ensureWorkflowCanPublish(WorkflowEntity entity) {
        Map<String, Object> normalized = workflowDslNormalizer.normalize(entity.getWorkflowDefinition(), "ORIN");
        entity.setWorkflowDefinition(normalized);
        workflowDslValidator.validateForPublishOrThrow(normalized);
        if (!workflowEngine.validateWorkflow(entity.getId())) {
            throw new IllegalStateException("Workflow validation failed: " + entity.getId());
        }
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
        return triggerWorkflowWithPriority(workflowId, inputs, TaskPriority.NORMAL, triggeredBy).getWorkflowInstanceId();
    }

    /**
     * 带优先级的工作流触发
     */
    public WorkflowExecutionSubmissionResponse triggerWorkflowWithPriority(Long workflowId, Map<String, Object> inputs,
                                                                           TaskPriority priority, String triggeredBy) {
        log.info("Triggering workflow: {}, priority: {}", workflowId, priority);
        return submitWorkflowExecution(workflowId, inputs, priority, triggeredBy, "API");
    }

    /**
     * 带优先级和外部 traceId 的工作流触发。
     */
    public WorkflowExecutionSubmissionResponse triggerWorkflowWithPriority(Long workflowId, Map<String, Object> inputs,
                                                                           TaskPriority priority, String triggeredBy,
                                                                           String traceId) {
        log.info("Triggering workflow: {}, priority: {}, traceIdProvided={}",
                workflowId, priority, traceId != null && !traceId.isBlank());
        return submitWorkflowExecution(workflowId, inputs, priority, triggeredBy, "API", traceId);
    }

    /**
     * 触发工作流（异步队列执行）
     */
    public Map<String, Object> triggerWorkflowAsync(Long workflowId, Map<String, Object> inputs,
                                                     TaskPriority priority, String triggeredBy) {
        log.info("Triggering workflow async: {}, priority: {}", workflowId, priority);

        WorkflowExecutionSubmissionResponse submission =
                submitWorkflowExecution(workflowId, inputs, priority, triggeredBy, "API_GATEWAY");

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", submission.getTaskId());
        result.put("workflowId", submission.getWorkflowId());
        result.put("workflowInstanceId", submission.getWorkflowInstanceId());
        result.put("traceId", submission.getTraceId());
        result.put("status", submission.getStatus());
        result.put("statusUrl", submission.getStatusUrl());
        result.put("instanceUrl", submission.getInstanceUrl());
        result.put("message", submission.getMessage());

        return result;
    }

    @Transactional
    public WorkflowExecutionSubmissionResponse submitWorkflowExecution(Long workflowId, Map<String, Object> inputs,
                                                                       TaskPriority priority, String triggeredBy,
                                                                       String triggerSource) {
        return submitWorkflowExecution(workflowId, inputs, priority, triggeredBy, triggerSource, null);
    }

    @Transactional
    public WorkflowExecutionSubmissionResponse submitWorkflowExecution(Long workflowId, Map<String, Object> inputs,
                                                                       TaskPriority priority, String triggeredBy,
                                                                       String triggerSource, String traceId) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
        // 资源级 ACL 第 3 刀: 非 owner / 非 admin/operator 拒绝执行
        workflowOwnershipResolver.assertCanManage(workflow);
        if (workflow.getStatus() == WorkflowEntity.WorkflowStatus.ARCHIVED) {
            throw new IllegalStateException("Workflow is archived and cannot be executed: " + workflowId);
        }
        if (workflow.getStatus() != WorkflowEntity.WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Workflow must be published before execution: " + workflowId);
        }
        Map<String, Object> normalizedDefinition = workflowDslNormalizer.normalize(workflow.getWorkflowDefinition(), "ORIN");
        workflow.setWorkflowDefinition(normalizedDefinition);
        workflowDslValidator.validateForPublishOrThrow(normalizedDefinition);
        workflowRepository.save(workflow);
        if (!workflowEngine.validateWorkflow(workflowId)) {
            throw new IllegalStateException("Workflow validation failed: " + workflowId);
        }

        Map<String, Object> executionInputs = inputs != null ? inputs : new HashMap<>();
        WorkflowInstanceEntity instance = traceId == null || traceId.isBlank()
                ? workflowEngine.createInstance(workflowId, executionInputs, triggeredBy)
                : workflowEngine.createInstance(workflowId, executionInputs, triggeredBy, traceId);
        Integer maxRetriesOverride = resolveTaskMaxRetries(workflow);
        TaskEntity task = maxRetriesOverride == null
                ? taskService.createAndEnqueueTask(
                        workflowId,
                        instance.getId(),
                        executionInputs,
                        priority,
                        triggeredBy,
                        triggerSource)
                : taskService.createAndEnqueueTask(
                        workflowId,
                        instance.getId(),
                        executionInputs,
                        priority,
                        triggeredBy,
                        triggerSource,
                        maxRetriesOverride);
        if (task.getStatus() == TaskEntity.TaskStatus.FAILED) {
            instance.setStatus(WorkflowInstanceEntity.InstanceStatus.FAILED);
            instance.setErrorMessage(task.getErrorMessage());
            instance.setErrorStack(task.getErrorStack());
            instance.setCompletedAt(LocalDateTime.now());
            if (instance.getStartedAt() != null) {
                instance.setDurationMs(Duration.between(instance.getStartedAt(), instance.getCompletedAt()).toMillis());
            }
            instanceRepository.save(instance);
        }

        log.info("Workflow task enqueued: taskId={}, workflowId={}, instanceId={}",
                task.getTaskId(), workflowId, instance.getId());

        return WorkflowExecutionSubmissionResponse.from(task, instance);
    }

    @SuppressWarnings("unchecked")
    private Integer resolveTaskMaxRetries(WorkflowEntity workflow) {
        if (workflow == null) {
            return null;
        }
        Integer retryPolicyMaxRetries = resolveMaxRetriesValue(workflow.getRetryPolicy());
        if (retryPolicyMaxRetries != null) {
            return retryPolicyMaxRetries;
        }
        Map<String, Object> workflowDefinition = workflow.getWorkflowDefinition();
        if (workflowDefinition == null) {
            return null;
        }
        Map<String, Object> metadata = workflowDefinition.get("metadata") instanceof Map<?, ?> rawMetadata
                ? (Map<String, Object>) rawMetadata
                : Map.of();
        Map<String, Object> execution = metadata.get("execution") instanceof Map<?, ?> rawExecution
                ? (Map<String, Object>) rawExecution
                : Map.of();
        Object value = execution.getOrDefault("maxRetries", metadata.get("maxRetries"));
        return resolveMaxRetriesValue(value);
    }

    private Integer resolveMaxRetriesValue(Map<String, Object> retryPolicy) {
        if (retryPolicy == null) {
            return null;
        }
        Object value = retryPolicy.getOrDefault("maxRetries", retryPolicy.get("max_retries"));
        return resolveMaxRetriesValue(value);
    }

    private Integer resolveMaxRetriesValue(Object value) {
        if (value instanceof Number number) {
            int retries = number.intValue();
            return retries >= 0 ? retries : null;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                int retries = Integer.parseInt(text.trim());
                return retries >= 0 ? retries : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public List<WorkflowResponse> getAllWorkflows() {
        // 资源级 ACL 第 3 刀: admin / operator 看全部, 普通用户按 owner 过滤
        List<WorkflowEntity> entities;
        if (workflowOwnershipResolver.isCurrentUserPrivileged()) {
            entities = workflowRepository.findAll();
        } else {
            Long currentUserId = workflowOwnershipResolver.resolveFromCurrentRequest();
            entities = workflowRepository.findByOwnerUserId(currentUserId);
        }
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public WorkflowResponse getWorkflowById(Long id) {
        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        // 资源级 ACL 第 3 刀: 非 owner / 非 admin/operator 拒绝读取
        workflowOwnershipResolver.assertCanManage(entity);
        return toResponse(entity);
    }

    public WorkflowInstanceEntity getInstance(Long instanceId) {
        WorkflowInstanceEntity instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow instance not found: " + instanceId));
        // 资源级 ACL 第 3 刀: instance 跟随所属 workflow 的 owner
        WorkflowEntity workflow = workflowRepository.findById(instance.getWorkflowId())
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + instance.getWorkflowId()));
        workflowOwnershipResolver.assertCanManage(workflow);
        return instance;
    }

    public List<WorkflowInstanceEntity> getWorkflowInstances(Long workflowId) {
        // 资源级 ACL 第 3 刀: instance 跟随所属 workflow 的 owner
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
        workflowOwnershipResolver.assertCanManage(workflow);
        return instanceRepository.findByWorkflowIdOrderByStartedAtDesc(workflowId);
    }

    @Transactional
    public void deleteWorkflow(Long id) {
        log.info("Deleting workflow: {}", id);
        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        // 资源级 ACL 第 3 刀: 非 owner / 非 admin/operator 拒绝删除
        workflowOwnershipResolver.assertCanManage(entity);

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

    public String exportDifyWorkflow(Long id) {
        log.info("Exporting workflow {} as Dify DSL", id);

        WorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));

        Map<String, Object> definition = workflowDslNormalizer.normalize(entity.getWorkflowDefinition(), "ORIN");
        return difyDslConverter.export(
                definition,
                entity.getWorkflowName(),
                entity.getDescription(),
                entity.getVersion());
    }

    private WorkflowResponse toResponse(WorkflowEntity entity) {
        Map<String, Object> normalizedDefinition = workflowDslNormalizer.normalize(entity.getWorkflowDefinition(), "ORIN");
        return WorkflowResponse.builder()
                .id(entity.getId())
                .workflowName(entity.getWorkflowName())
                .description(entity.getDescription())
                .ownerUserId(entity.getOwnerUserId())
                .mcpExposed(entity.isMcpExposed())
                .workflowType(entity.getWorkflowType())
                .workflowDefinition(normalizedDefinition)
                .timeoutSeconds(entity.getTimeoutSeconds())
                .status(entity.getStatus())
                .version(entity.getVersion())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
