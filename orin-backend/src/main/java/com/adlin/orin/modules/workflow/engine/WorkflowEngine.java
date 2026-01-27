package com.adlin.orin.modules.workflow.engine;

import com.adlin.orin.modules.agent.service.AgentExecutor;
import com.adlin.orin.modules.skill.service.SkillService;
import com.adlin.orin.modules.trace.interceptor.SkillTraceInterceptor;

import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowStepEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 工作流编排引擎
 * 负责执行工作流,支持顺序、并行和 DAG 执行模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowStepRepository stepRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final SkillService skillService;
    private final AgentExecutor agentExecutor;
    private final GraphExecutor graphExecutor;
    private final com.adlin.orin.modules.workflow.repository.WorkflowRepository workflowRepository;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 执行工作流
     *
     * @param workflowId  工作流 ID
     * @param inputs      输入数据
     * @param triggeredBy 触发人
     * @return 工作流实例 ID
     */
    public Long executeWorkflow(Long workflowId, Map<String, Object> inputs, String triggeredBy) {
        log.info("Starting workflow execution (sync): workflowId={}, triggeredBy={}", workflowId, triggeredBy);
        WorkflowInstanceEntity instance = createInstance(workflowId, inputs, triggeredBy);
        executeInstance(instance.getId());
        return instance.getId();
    }

    /**
     * 异步执行工作流实例
     */
    @Async("taskExecutor")
    public void executeInstanceAsync(Long instanceId) {
        log.info("Starting workflow execution (async): instanceId={}", instanceId);
        executeInstance(instanceId);
    }

    /**
     * 执行工作流实例核心逻辑
     */
    public void executeInstance(Long instanceId) {
        WorkflowInstanceEntity instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + instanceId));

        Long workflowId = instance.getWorkflowId();

        try {
            // 获取工作流定义
            com.adlin.orin.modules.workflow.entity.WorkflowEntity workflow = this.workflowRepository
                    .findById(workflowId)
                    .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

            Map<String, Object> workflowDefinition = workflow.getWorkflowDefinition();

            // 检查 Dify DSL 结构 (workflow.graph)
            Map<String, Object> graphDefinition = null;
            if (workflowDefinition != null) {
                if (workflowDefinition.containsKey("workflow")) {
                    Map<String, Object> workflowObj = (Map<String, Object>) workflowDefinition.get("workflow");
                    if (workflowObj != null && workflowObj.containsKey("graph")) {
                        graphDefinition = (Map<String, Object>) workflowObj.get("graph");
                    }
                } else if (workflowDefinition.containsKey("graph")) {
                    graphDefinition = (Map<String, Object>) workflowDefinition.get("graph");
                } else if (workflowDefinition.containsKey("nodes")) {
                    // 兼容旧的扁平结构
                    graphDefinition = workflowDefinition;
                }
            }

            // 检查是否为图结构工作流
            boolean isGraphBased = graphDefinition != null &&
                    graphDefinition.containsKey("nodes") &&
                    graphDefinition.get("nodes") != null;

            Map<String, Object> context = new HashMap<>(instance.getInputData());
            context.put("__traceId", instance.getTraceId());
            context.put("__instanceId", instance.getId());
            Map<String, Object> outputs;

            if (isGraphBased) {
                // 使用图执行器
                log.info("Executing graph-based workflow");
                // IMPORTANT: Pass instanceId to GraphExecutor for event publishing
                graphExecutor.setInstanceId(instance.getId());
                outputs = graphExecutor.executeGraph(graphDefinition, context);
            } else {
                // 使用传统步骤执行器
                log.info("Executing step-based workflow");
                outputs = executeStepBasedWorkflow(workflowId, context);
            }

            // 更新实例状态为成功
            completeInstance(instance, outputs, null);
            log.info("Workflow execution completed successfully: instanceId={}", instance.getId());

        } catch (Exception e) {
            log.error("Workflow execution failed: instanceId={}, error={}", instanceId, e.getMessage(), e);
            completeInstance(instance, null, e);
            // Don't rethrow in async method, just log
        }
    }

    /**
     * 执行基于步骤的工作流（传统模式）
     */
    private Map<String, Object> executeStepBasedWorkflow(Long workflowId, Map<String, Object> context) {
        // 获取所有步骤
        List<WorkflowStepEntity> steps = stepRepository.findByWorkflowIdOrderByStepOrderAsc(workflowId);
        if (steps.isEmpty()) {
            throw new IllegalStateException("Workflow has no steps: " + workflowId);
        }

        Map<String, Object> stepOutputs = new HashMap<>();

        for (WorkflowStepEntity step : steps) {
            // 检查依赖
            if (!checkDependencies(step, stepOutputs)) {
                log.warn("Step dependencies not met, skipping: {}", step.getStepName());
                continue;
            }

            // 评估条件
            if (!evaluateCondition(step, context)) {
                log.info("Step condition not met, skipping: {}", step.getStepName());
                continue;
            }

            // 执行步骤
            Map<String, Object> stepResult = executeStep(step, context, stepOutputs);
            stepOutputs.put(step.getId().toString(), stepResult);

            // 更新上下文
            context.putAll(stepResult);
        }

        return stepOutputs;
    }

    /**
     * 执行单个步骤
     */
    private Map<String, Object> executeStep(
            WorkflowStepEntity step,
            Map<String, Object> context,
            Map<String, Object> stepOutputs) {

        log.info("Executing step: {} (type: {})", step.getStepName(), step.getStepType());

        // 设置追踪上下文
        String traceId = (String) context.get("__traceId");
        Long instanceId = (Long) context.get("__instanceId");

        if (traceId != null && instanceId != null) {
            SkillTraceInterceptor.TraceContext.setTraceId(traceId);
            SkillTraceInterceptor.TraceContext.setInstanceId(instanceId);
            SkillTraceInterceptor.TraceContext.setStepId(step.getId());
            SkillTraceInterceptor.TraceContext.setStepName(step.getStepName());
        }

        try {
            // 解析输入参数
            Map<String, Object> inputs = resolveParameters(step.getInputMapping(), context, stepOutputs);

            Map<String, Object> result;

            // 根据步骤类型选择执行器
            if (step.getStepType() == WorkflowStepEntity.StepType.AGENT) {
                // 执行智能体
                if (step.getAgentId() == null) {
                    throw new IllegalStateException("Agent ID is required for AGENT step: " + step.getStepName());
                }
                log.info("Invoking agent: agentId={}", step.getAgentId());
                result = agentExecutor.executeAgent(step.getAgentId(), inputs);

            } else if (step.getStepType() == WorkflowStepEntity.StepType.SKILL) {
                // 执行技能
                if (step.getSkillId() == null) {
                    throw new IllegalStateException("Skill ID is required for SKILL step: " + step.getStepName());
                }
                log.info("Invoking skill: skillId={}", step.getSkillId());
                result = skillService.executeSkill(step.getSkillId(), inputs);

            } else if (step.getStepType() == WorkflowStepEntity.StepType.LOGIC) {
                // 逻辑控制步骤 (暂时返回输入)
                log.info("Executing LOGIC step (pass-through)");
                result = new HashMap<>(inputs);
                result.put("success", true);

            } else {
                throw new IllegalStateException("Unsupported step type: " + step.getStepType());
            }

            // 应用输出映射
            if (step.getOutputMapping() != null) {
                result = applyOutputMapping(result, step.getOutputMapping());
            }

            log.info("Step executed successfully: {}", step.getStepName());
            return result;

        } catch (Exception e) {
            log.error("Step execution failed: {}, error={}", step.getStepName(), e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        } finally {
            // 清理追踪上下文
            SkillTraceInterceptor.TraceContext.clear();
        }
    }

    /**
     * 解析参数映射
     * 支持从上下文和上游步骤输出中获取值
     */
    private Map<String, Object> resolveParameters(
            Map<String, Object> mapping,
            Map<String, Object> context,
            Map<String, Object> stepOutputs) {

        if (mapping == null) {
            return context;
        }

        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                String strValue = (String) value;
                // 支持 ${context.key} 和 ${step.stepId.key} 语法
                if (strValue.startsWith("${") && strValue.endsWith("}")) {
                    String expression = strValue.substring(2, strValue.length() - 1);
                    Object resolvedValue = resolveExpression(expression, context, stepOutputs);
                    resolved.put(key, resolvedValue);
                } else {
                    resolved.put(key, value);
                }
            } else {
                resolved.put(key, value);
            }
        }

        return resolved;
    }

    /**
     * 解析表达式
     */
    private Object resolveExpression(String expression, Map<String, Object> context, Map<String, Object> stepOutputs) {
        if (expression.startsWith("context.")) {
            String key = expression.substring(8);
            return context.get(key);
        } else if (expression.startsWith("step.")) {
            String[] parts = expression.substring(5).split("\\.", 2);
            String stepId = parts[0];
            String key = parts.length > 1 ? parts[1] : null;

            @SuppressWarnings("unchecked")
            Map<String, Object> stepOutput = (Map<String, Object>) stepOutputs.get(stepId);
            if (stepOutput != null && key != null) {
                return stepOutput.get(key);
            }
            return stepOutput;
        }
        return expression;
    }

    /**
     * 应用输出映射
     */
    private Map<String, Object> applyOutputMapping(Map<String, Object> result, Map<String, Object> mapping) {
        Map<String, Object> mapped = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String targetKey = entry.getKey();
            String sourceKey = (String) entry.getValue();
            mapped.put(targetKey, result.get(sourceKey));
        }
        return mapped;
    }

    /**
     * 检查步骤依赖是否满足
     */
    private boolean checkDependencies(WorkflowStepEntity step, Map<String, Object> stepOutputs) {
        if (step.getDependsOn() == null || step.getDependsOn().isEmpty()) {
            return true;
        }

        for (Long dependencyId : step.getDependsOn()) {
            if (!stepOutputs.containsKey(dependencyId.toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(WorkflowStepEntity step, Map<String, Object> context) {
        if (step.getConditionExpression() == null || step.getConditionExpression().trim().isEmpty()) {
            return true;
        }

        try {
            Expression expression = expressionParser.parseExpression(step.getConditionExpression());
            StandardEvaluationContext evalContext = new StandardEvaluationContext();
            evalContext.setVariables(context);

            Boolean result = expression.getValue(evalContext, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.error("Failed to evaluate condition: {}, error={}", step.getConditionExpression(), e.getMessage());
            return false;
        }
    }

    /**
     * 创建工作流实例
     */
    public WorkflowInstanceEntity createInstance(Long workflowId, Map<String, Object> inputs, String triggeredBy) {
        WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                .workflowId(workflowId)
                .traceId(UUID.randomUUID().toString())
                .status(WorkflowInstanceEntity.InstanceStatus.RUNNING)
                .inputData(inputs)
                .triggeredBy(triggeredBy)
                .triggerSource("API")
                .startedAt(LocalDateTime.now())
                .build();

        return instanceRepository.save(instance);
    }

    /**
     * 完成工作流实例
     */
    private void completeInstance(WorkflowInstanceEntity instance, Map<String, Object> outputs, Exception error) {
        instance.setCompletedAt(LocalDateTime.now());
        instance.setDurationMs(
                java.time.Duration.between(instance.getStartedAt(), instance.getCompletedAt()).toMillis());

        if (error != null) {
            instance.setStatus(WorkflowInstanceEntity.InstanceStatus.FAILED);
            instance.setErrorMessage(error.getMessage());
            instance.setErrorStack(getStackTrace(error));
        } else {
            instance.setStatus(WorkflowInstanceEntity.InstanceStatus.SUCCESS);
            instance.setOutputData(outputs);
        }

        instanceRepository.save(instance);
    }

    /**
     * 获取异常堆栈信息
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 验证工作流定义 (检测循环依赖)
     */
    public boolean validateWorkflow(Long workflowId) {
        List<WorkflowStepEntity> steps = stepRepository.findByWorkflowIdOrderByStepOrderAsc(workflowId);

        // 构建依赖图
        Map<Long, List<Long>> graph = new HashMap<>();
        for (WorkflowStepEntity step : steps) {
            graph.put(step.getId(), step.getDependsOn() != null ? step.getDependsOn() : new ArrayList<>());
        }

        // 检测循环依赖
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();

        for (Long stepId : graph.keySet()) {
            if (hasCycle(stepId, graph, visited, recursionStack)) {
                log.error("Cycle detected in workflow: {}", workflowId);
                return false;
            }
        }

        return true;
    }

    /**
     * 检测循环依赖 (DFS)
     */
    private boolean hasCycle(Long stepId, Map<Long, List<Long>> graph, Set<Long> visited, Set<Long> recursionStack) {
        if (recursionStack.contains(stepId)) {
            return true;
        }
        if (visited.contains(stepId)) {
            return false;
        }

        visited.add(stepId);
        recursionStack.add(stepId);

        List<Long> dependencies = graph.get(stepId);
        if (dependencies != null) {
            for (Long dep : dependencies) {
                if (hasCycle(dep, graph, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(stepId);
        return false;
    }
}
