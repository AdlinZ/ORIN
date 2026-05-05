package com.adlin.orin.modules.playground.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.playground.entity.PlaygroundConversationEntity;
import com.adlin.orin.modules.playground.entity.PlaygroundMessageEntity;
import com.adlin.orin.modules.playground.entity.PlaygroundRunEntity;
import com.adlin.orin.modules.playground.entity.PlaygroundWorkflowEntity;
import com.adlin.orin.modules.playground.repository.PlaygroundConversationRepository;
import com.adlin.orin.modules.playground.repository.PlaygroundMessageRepository;
import com.adlin.orin.modules.playground.repository.PlaygroundRunRepository;
import com.adlin.orin.modules.playground.repository.PlaygroundWorkflowRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.adlin.orin.common.exception.WorkflowExecutionException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundService {

    private static final String DEFAULT_ROUTER_PROMPT =
            "You are an orchestration router. Select the best specialist based on user intent.";
    private static final String EXECUTION_MODE_DYNAMIC = "DYNAMIC";
    private static final String EXECUTION_MODE_DAG_STRICT = "DAG_STRICT";
    private static final int DEFAULT_AGENT_MAX_TOKENS = 2400;
    private static final int MIN_AGENT_MAX_TOKENS = 256;
    private static final int MAX_AGENT_MAX_TOKENS = 16000;

    private final AgentMetadataRepository agentRepository;
    private final PlaygroundWorkflowRepository workflowRepository;
    private final PlaygroundConversationRepository conversationRepository;
    private final PlaygroundMessageRepository messageRepository;
    private final PlaygroundRunRepository runRepository;
    private final PlaygroundGraphFactory graphFactory;
    private final PlaygroundRuntimeClient runtimeClient;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> templates() {
        return List.of(
                template("router_specialists", "Router Specialists",
                        "Router selects the best specialist for the user intent, then optionally passes through a finalizer.", 1),
                template("planner_executor", "Planner Executor",
                        "Planner decomposes the request into sub-tasks, delegates each task to workers, then synthesizes a final answer.", 2),
                template("supervisor_dynamic", "Supervisor Dynamic",
                        "Supervisor decides delegation at runtime, loops through workers as needed, and composes the final answer.", 2),
                template("single_agent_chat", "Single Agent Chat",
                        "Direct chat with one selected agent using a minimal start -> agent -> end graph.", 1),
                template("peer_handoff", "Peer Handoff",
                        "Router selects the first owner, then specialists hand work to each other inside a shared collaboration zone.", 2)
        );
    }

    public List<Map<String, Object>> listAgents() {
        ensureDefaultAgent();
        return agentRepository.findAll().stream()
                .sorted(Comparator.comparing(agent -> safe(agent.getName())))
                .map(this::agentDto)
                .toList();
    }

    @Transactional
    public Map<String, Object> createAgent(Map<String, Object> payload) {
        String id = uuid();
        AgentMetadata agent = new AgentMetadata();
        agent.setAgentId(id);
        applyAgentPayload(agent, payload);
        agent.setSyncTime(LocalDateTime.now());
        return agentDto(agentRepository.save(agent));
    }

    @Transactional
    public Map<String, Object> updateAgent(String id, Map<String, Object> payload) {
        AgentMetadata agent = agentRepository.findById(id)
                .orElseThrow(() -> notFound("Agent not found."));
        applyAgentPayload(agent, payload);
        agent.setSyncTime(LocalDateTime.now());
        return agentDto(agentRepository.save(agent));
    }

    @Transactional
    public Map<String, Object> deleteAgent(String id) {
        if (!workflowRepository.findBySpecialistAgentIdsJsonContaining(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent is still used by workflow(s).");
        }
        if (!agentRepository.existsById(id)) {
            throw notFound("Agent not found.");
        }
        agentRepository.deleteById(id);
        return Map.of("deleted", true);
    }

    public List<Map<String, Object>> listWorkflows() {
        ensureDefaultWorkflow();
        return workflowRepository.findAll().stream()
                .sorted(Comparator.comparing(PlaygroundWorkflowEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::workflowDto)
                .toList();
    }

    @Transactional
    public Map<String, Object> createWorkflow(Map<String, Object> payload) {
        PlaygroundWorkflowEntity entity = new PlaygroundWorkflowEntity();
        entity.setId(uuid());
        applyWorkflowPayload(entity, payload);
        return workflowDto(workflowRepository.save(entity));
    }

    @Transactional
    public Map<String, Object> updateWorkflow(String workflowId, Map<String, Object> payload) {
        PlaygroundWorkflowEntity entity = workflowRepository.findById(workflowId)
                .orElseThrow(() -> notFound("Workflow not found."));
        applyWorkflowPayload(entity, payload);
        return workflowDto(workflowRepository.save(entity));
    }

    @Transactional
    public Map<String, Object> deleteWorkflow(String workflowId) {
        if (!workflowRepository.existsById(workflowId)) {
            throw notFound("Workflow not found.");
        }
        workflowRepository.deleteById(workflowId);
        return Map.of("deleted", true);
    }

    public Map<String, Object> getWorkflowGraph(String workflowId) {
        PlaygroundWorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> notFound("Workflow not found."));
        return graphFactory.buildGraph(workflowDto(workflow), resolveAgents(parseStringList(workflow.getSpecialistAgentIdsJson())));
    }

    public List<Map<String, Object>> listConversations(String workflowId) {
        List<PlaygroundConversationEntity> conversations = workflowId == null || workflowId.isBlank()
                ? conversationRepository.findAllByOrderByUpdatedAtDesc()
                : conversationRepository.findByWorkflowIdOrderByUpdatedAtDesc(workflowId);
        return conversations.stream().map(this::conversationDto).toList();
    }

    @Transactional
    public Map<String, Object> createConversation(Map<String, Object> payload) {
        String workflowId = requireText(payload.get("workflow_id"), "workflow_id is required.");
        if (!workflowRepository.existsById(workflowId)) {
            throw notFound("Workflow not found.");
        }
        PlaygroundConversationEntity entity = new PlaygroundConversationEntity();
        entity.setId(uuid());
        entity.setWorkflowId(workflowId);
        entity.setTitle("New Conversation");
        return conversationDto(conversationRepository.save(entity));
    }

    public Map<String, Object> getConversation(String conversationId) {
        PlaygroundConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> notFound("Conversation not found."));
        Map<String, Object> dto = new LinkedHashMap<>(conversationDto(conversation));
        dto.put("messages", messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream().map(this::messageDto).toList());
        return dto;
    }

    @Transactional
    public Map<String, Object> deleteConversation(String conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw notFound("Conversation not found.");
        }
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
        return Map.of("deleted", true);
    }

    @Transactional
    public Map<String, Object> runWorkflow(Map<String, Object> payload, String userId, String traceId) {
        return runWorkflowInternal(payload, userId, traceId, null);
    }

    @Transactional
    public Map<String, Object> runWorkflowStream(Map<String, Object> payload, String userId, String traceId,
                                                 BiConsumer<String, Object> eventConsumer) {
        return runWorkflowInternal(payload, userId, traceId, eventConsumer);
    }

    private Map<String, Object> runWorkflowInternal(Map<String, Object> payload, String userId, String traceId,
                                                   BiConsumer<String, Object> eventConsumer) {
        long started = System.currentTimeMillis();
        String workflowId = requireText(payload.get("workflow_id"), "workflow_id is required.");
        String userInput = requireText(payload.get("user_input"), "user_input is required.");
        PlaygroundWorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> notFound("Workflow not found."));
        Map<String, Object> workflowDto = workflowDto(workflow);
        if (payload.containsKey("agent_max_tokens")) {
            workflowDto.put("agent_max_tokens", normalizeAgentMaxTokens(payload.get("agent_max_tokens")));
        }
        if (payload.containsKey("merge_max_tokens")) {
            workflowDto.put("merge_max_tokens", normalizeAgentMaxTokens(payload.get("merge_max_tokens")));
        }
        List<Map<String, Object>> agents = resolveAgents(parseStringList(workflow.getSpecialistAgentIdsJson()));
        List<Map<String, Object>> ephemeralAgents = sanitizeEphemeralAgents(payload.get("ephemeral_agents"));
        List<Map<String, Object>> contextMessages = sanitizeContextMessages(payload.get("context_messages"));
        String conversationId = ensureConversation(workflowId, text(payload.get("conversation_id"), null), userInput);
        saveMessage(conversationId, "user", userInput, null);

        PlaygroundRunEntity run = new PlaygroundRunEntity();
        run.setId(uuid());
        run.setWorkflowId(workflowId);
        run.setConversationId(conversationId);
        run.setTraceId(traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
        run.setUserId(userId);
        run.setWorkflowType(text(workflowDto.get("type"), "router_specialists"));
        run.setStatus("RUNNING");
        run.setUserInput(userInput);
        run = runRepository.save(run);

        Map<String, Object> result;
        try {
            if (eventConsumer != null) {
                result = callRuntimeStream(run.getId(), workflowDto, agents, ephemeralAgents, contextMessages, userInput, conversationId, eventConsumer);
            } else {
                result = callRuntime(run.getId(), workflowDto, agents, ephemeralAgents, contextMessages, userInput, conversationId);
            }
        } catch (Exception e) {
            String normalizedError = resolveErrorMessage(e);
            log.error("Playground ai-engine runtime failed: {}", normalizedError, e);
            run.setStatus("FAILED");
            run.setDurationMs(System.currentTimeMillis() - started);
            run.setAssistantMessage("");
            run.setArtifactsJson(toJson(Map.of(
                    "execution_path", "langgraph_mq",
                    "error_message", normalizedError
            )));
            runRepository.save(run);
            throw new WorkflowExecutionException("Playground runtime execution failed: " + normalizedError, e);
        }

        String runtimeStatus = text(result.get("status"), "success");
        if ("error".equalsIgnoreCase(runtimeStatus)) {
            String runtimeError = text(result.get("error"), text(result.get("error_message"), "Playground runtime returned error status."));
            run.setStatus("FAILED");
            run.setDurationMs(System.currentTimeMillis() - started);
            run.setAssistantMessage("");
            run.setTraceJson(toJson(result.get("trace")));
            run.setGraphJson(toJson(result.get("graph")));
            run.setArtifactsJson(toJson(Map.of(
                    "execution_path", "langgraph_mq",
                    "runtime_status", runtimeStatus,
                    "error_message", runtimeError
            )));
            runRepository.save(run);
            throw new WorkflowExecutionException("Playground runtime execution failed: " + runtimeError);
        }

        String assistantMessage = text(result.get("assistant_message"), "No response generated.");
        Map<String, Object> artifacts = objectMap(result.get("artifacts"));
        if ("partial".equalsIgnoreCase(runtimeStatus)) {
            artifacts.put("runtime_status", runtimeStatus);
            Object runtimeError = result.get("error") != null ? result.get("error") : result.get("error_message");
            if (runtimeError != null) {
                artifacts.put("error_message", runtimeError);
            }
        }
        saveMessage(conversationId, "assistant", assistantMessage, text(artifacts.get("route_agent_name"), "Assistant"));

        run.setStatus("partial".equalsIgnoreCase(runtimeStatus) ? "PARTIAL" : "COMPLETED");
        run.setAssistantMessage(assistantMessage);
        run.setDurationMs(System.currentTimeMillis() - started);
        run.setTraceJson(toJson(result.get("trace")));
        run.setGraphJson(toJson(result.get("graph")));
        run.setArtifactsJson(toJson(artifacts));
        runRepository.save(run);

        result.put("run_id", run.getId());
        result.put("conversation_id", conversationId);
        result.put("workflow_id", workflowId);
        return result;
    }

    public Map<String, Object> appSettings() {
        return Map.of(
                "model_profiles", List.of(),
                "active_model_profile_id", "",
                "env_vars", List.of(),
                "env_path", ""
        );
    }

    public List<Map<String, Object>> skills() {
        return List.of();
    }

    public Map<String, Object> createSkill(Map<String, Object> payload) {
        return Map.of(
                "id", uuid(),
                "name", text(payload.get("name"), "Skill"),
                "description", text(payload.get("description"), ""),
                "instruction", text(payload.get("instruction"), ""),
                "skill_ids", List.of()
        );
    }

    private Map<String, Object> callRuntime(String runId, Map<String, Object> workflow,
                                            List<Map<String, Object>> agents,
                                            List<Map<String, Object>> ephemeralAgents,
                                            List<Map<String, Object>> contextMessages,
                                            String userInput,
                                            String conversationId) {
        Map<String, Object> runtimePayload = new LinkedHashMap<>();
        runtimePayload.put("run_id", runId);
        runtimePayload.put("workflow", workflow);
        runtimePayload.put("agents", agents);
        runtimePayload.put("ephemeral_agents", ephemeralAgents);
        runtimePayload.put("context_messages", contextMessages);
        runtimePayload.put("user_input", userInput);
        runtimePayload.put("conversation_id", conversationId);
        return runtimeClient.run(runtimePayload);
    }

    private Map<String, Object> callRuntimeStream(String runId, Map<String, Object> workflow,
                                                  List<Map<String, Object>> agents,
                                                  List<Map<String, Object>> ephemeralAgents,
                                                  List<Map<String, Object>> contextMessages,
                                                  String userInput,
                                                  String conversationId,
                                                  BiConsumer<String, Object> eventConsumer) {
        Map<String, Object> runtimePayload = new LinkedHashMap<>();
        runtimePayload.put("run_id", runId);
        runtimePayload.put("workflow", workflow);
        runtimePayload.put("agents", agents);
        runtimePayload.put("ephemeral_agents", ephemeralAgents);
        runtimePayload.put("context_messages", contextMessages);
        runtimePayload.put("user_input", userInput);
        runtimePayload.put("conversation_id", conversationId);
        return runtimeClient.runStream(runtimePayload, eventConsumer);
    }

    @Transactional
    protected void ensureDefaultWorkflow() {
        if (!workflowRepository.findAll().isEmpty()) {
            return;
        }
        AgentMetadata agent = ensureDefaultAgent();
        PlaygroundWorkflowEntity workflow = new PlaygroundWorkflowEntity();
        workflow.setId("playground_default_single_agent");
        workflow.setName("默认单 Agent 对话");
        workflow.setType("single_agent_chat");
        workflow.setSpecialistAgentIdsJson(toJson(List.of(agent.getAgentId())));
        workflow.setFinalizerEnabled(false);
        workflow.setRouterPrompt("Direct single-agent chat workflow.");
        workflow.setExecutionMode(EXECUTION_MODE_DYNAMIC);
        workflow.setDagSubtasksJson("[]");
        workflow.setAgentMaxTokens(DEFAULT_AGENT_MAX_TOKENS);
        workflowRepository.save(workflow);
    }

    @Transactional
    protected AgentMetadata ensureDefaultAgent() {
        String defaultAgentId = "playground_default_assistant";
        return agentRepository.findById(defaultAgentId).orElseGet(() -> {
            AgentMetadata agent = new AgentMetadata();
            agent.setAgentId(defaultAgentId);
            agent.setName("ORIN 默认助手");
            agent.setDescription("用于 Playground 首次运行的默认单 Agent。");
            agent.setSystemPrompt("You are ORIN default assistant. Answer clearly and keep runtime trace concise.");
            agent.setModelName("");
            agent.setProviderType("PLAYGROUND");
            agent.setMode("agent");
            agent.setViewType("CHAT");
            agent.setParameters(toJson(Map.of("skill_ids", List.of(), "builtin_capabilities", List.of())));
            agent.setSyncTime(LocalDateTime.now());
            return agentRepository.save(agent);
        });
    }

    private Map<String, Object> fallbackRun(String runId, Map<String, Object> workflow,
                                            List<Map<String, Object>> agents, String userInput,
                                            String conversationId) {
        Map<String, Object> graph = graphFactory.buildGraph(workflow, agents);
        String type = text(workflow.get("type"), "router_specialists");
        Map<String, Object> selected = agents.isEmpty() ? Map.of("id", "assistant", "name", "Assistant") : agents.get(0);
        List<Map<String, Object>> trace = new ArrayList<>();
        trace.add(trace("run_started", "Run Started", "Starting workflow: " + text(workflow.get("name"), type), "start", runId));
        trace.add(trace("node_entered", "Workflow Selected", "Using " + type + " topology.", typeNode(type), runId));
        trace.add(trace("route_selected", "Agent Selected", text(selected.get("name"), "Assistant") + " selected for the request.", text(selected.get("id"), "assistant"), runId));
        trace.add(trace("message_generated", "Message Generated", "Assistant response generated by ORIN fallback runtime.", text(selected.get("id"), "assistant"), runId));
        if (Boolean.TRUE.equals(workflow.get("finalizer_enabled"))) {
            trace.add(trace("node_entered", "Finalizer", "Final response synthesized.", "finalize", runId));
        }
        trace.add(trace("run_finished", "Run Finished", "Workflow completed.", "end", runId));
        String assistant = "ORIN Playground 已执行 " + type + " 工作流。\n\n"
                + "用户输入：" + userInput + "\n\n"
                + "当前同步 MVP 已完成图、Trace、会话与运行记录闭环；Agent 节点执行会在后续接入 MQ/Redis 检查点时替换为真实异步执行。";
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("route_agent_id", selected.get("id"));
        artifacts.put("route_agent_name", selected.get("name"));
        artifacts.put("route_reason", "Java fallback runtime selected the first available agent.");
        artifacts.put("specialist_answer", assistant);
        artifacts.put("final_answer", assistant);
        return new LinkedHashMap<>(Map.of(
                "workflow_id", workflow.get("id"),
                "user_input", userInput,
                "assistant_message", assistant,
                "trace", trace,
                "graph", graph,
                "artifacts", artifacts,
                "conversation_id", conversationId
        ));
    }

    private String ensureConversation(String workflowId, String conversationId, String userInput) {
        if (conversationId != null && !conversationId.isBlank() && conversationRepository.existsById(conversationId)) {
            return conversationId;
        }
        PlaygroundConversationEntity conversation = new PlaygroundConversationEntity();
        conversation.setId(uuid());
        conversation.setWorkflowId(workflowId);
        conversation.setTitle(userInput.length() > 48 ? userInput.substring(0, 48) : userInput);
        return conversationRepository.save(conversation).getId();
    }

    private String resolveErrorMessage(Throwable error) {
        if (error == null) {
            return "Unknown playground runtime error.";
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }
        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return error.getClass().getSimpleName();
    }

    private void saveMessage(String conversationId, String role, String content, String agentName) {
        PlaygroundMessageEntity message = new PlaygroundMessageEntity();
        message.setId(uuid());
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setAgentName(agentName);
        try {
            messageRepository.save(message);
        } catch (DataIntegrityViolationException e) {
            log.warn("Failed to save playground message: {}", e.getMessage());
        }
    }

    private void applyAgentPayload(AgentMetadata agent, Map<String, Object> payload) {
        agent.setName(requireText(payload.get("name"), "name is required."));
        agent.setDescription(requireText(payload.get("description"), "description is required."));
        agent.setSystemPrompt(requireText(payload.get("system_prompt"), "system_prompt is required."));
        agent.setModelName(text(payload.get("model"), null));
        agent.setProviderType("PLAYGROUND");
        agent.setMode("agent");
        agent.setViewType("CHAT");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("skill_ids", list(payload.get("skill_ids")));
        params.put("builtin_capabilities", list(payload.get("builtin_capabilities")));
        agent.setParameters(toJson(params));
    }

    private void applyWorkflowPayload(PlaygroundWorkflowEntity entity, Map<String, Object> payload) {
        String type = requireText(payload.get("type"), "type is required.");
        List<String> agentIds = stringList(payload.get("specialist_agent_ids"));
        String executionMode = normalizeExecutionMode(payload.get("execution_mode"));
        List<Map<String, Object>> dagSubtasks = mapList(payload.get("dag_subtasks"));
        validateWorkflow(type, agentIds);
        validateDagSubtasks(executionMode, dagSubtasks);
        entity.setName(requireText(payload.get("name"), "name is required."));
        entity.setType(type);
        entity.setSpecialistAgentIdsJson(toJson(agentIds));
        entity.setFinalizerEnabled(Boolean.TRUE.equals(payload.getOrDefault("finalizer_enabled", Boolean.TRUE)));
        entity.setRouterPrompt(text(payload.get("router_prompt"), DEFAULT_ROUTER_PROMPT));
        entity.setExecutionMode(executionMode);
        entity.setDagSubtasksJson(toJson(dagSubtasks));
        entity.setAgentMaxTokens(normalizeAgentMaxTokens(payload.get("agent_max_tokens")));
    }

    private void validateWorkflow(String type, List<String> agentIds) {
        int required = requiredAgentCount(type);
        if (agentIds.size() < required) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    type + " requires at least " + required + " agents.");
        }
        List<String> missing = agentIds.stream().filter(id -> !agentRepository.existsById(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "These agent IDs do not exist: " + missing);
        }
    }

    private int requiredAgentCount(String type) {
        return switch (type) {
            case "single_agent_chat", "router_specialists" -> 1;
            case "planner_executor", "supervisor_dynamic", "peer_handoff" -> 2;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported workflow type: " + type);
        };
    }

    private List<Map<String, Object>> resolveAgents(List<String> agentIds) {
        return agentIds.stream()
                .map(agentRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(this::agentDto)
                .toList();
    }

    private Map<String, Object> agentDto(AgentMetadata agent) {
        Map<String, Object> params = objectMap(readJson(agent.getParameters()));
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", agent.getAgentId());
        dto.put("name", safe(agent.getName()));
        dto.put("description", safe(agent.getDescription()));
        dto.put("system_prompt", safe(agent.getSystemPrompt()));
        dto.put("model", safe(agent.getModelName()));
        dto.put("skill_ids", list(params.get("skill_ids")));
        dto.put("builtin_capabilities", list(params.get("builtin_capabilities")));
        return dto;
    }

    private Map<String, Object> workflowDto(PlaygroundWorkflowEntity entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entity.getId());
        dto.put("name", entity.getName());
        dto.put("type", entity.getType());
        dto.put("specialist_agent_ids", parseStringList(entity.getSpecialistAgentIdsJson()));
        dto.put("router_prompt", text(entity.getRouterPrompt(), DEFAULT_ROUTER_PROMPT));
        dto.put("finalizer_enabled", Boolean.TRUE.equals(entity.getFinalizerEnabled()));
        dto.put("execution_mode", normalizeExecutionMode(entity.getExecutionMode()));
        dto.put("dag_subtasks", parseMapList(entity.getDagSubtasksJson()));
        dto.put("agent_max_tokens", normalizeAgentMaxTokens(entity.getAgentMaxTokens()));
        return dto;
    }

    private String normalizeExecutionMode(Object value) {
        String mode = text(value, EXECUTION_MODE_DYNAMIC).toUpperCase();
        if (!EXECUTION_MODE_DYNAMIC.equals(mode) && !EXECUTION_MODE_DAG_STRICT.equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported execution_mode: " + mode + ". Supported: DYNAMIC, DAG_STRICT");
        }
        return mode;
    }

    private void validateDagSubtasks(String executionMode, List<Map<String, Object>> dagSubtasks) {
        if (!EXECUTION_MODE_DAG_STRICT.equals(executionMode)) {
            return;
        }
        if (dagSubtasks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "DAG_STRICT requires non-empty dag_subtasks.");
        }
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> task : dagSubtasks) {
            String id = text(task.get("id"), "");
            if (id.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each DAG task requires non-empty id.");
            }
            if (!ids.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate DAG task id: " + id);
            }
            String description = text(task.get("description"), "");
            if (description.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "DAG task '" + id + "' requires non-empty description.");
            }
        }
        for (Map<String, Object> task : dagSubtasks) {
            String id = text(task.get("id"), "");
            List<String> deps = stringList(task.get("depends_on"));
            for (String dep : deps) {
                if (!ids.contains(dep)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "DAG task '" + id + "' depends_on unknown task: " + dep);
                }
            }
        }
    }

    private int normalizeAgentMaxTokens(Object value) {
        if (value == null) {
            return DEFAULT_AGENT_MAX_TOKENS;
        }
        int parsed;
        try {
            if (value instanceof Number number) {
                parsed = number.intValue();
            } else {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "agent_max_tokens must be an integer.");
        }
        if (parsed < MIN_AGENT_MAX_TOKENS || parsed > MAX_AGENT_MAX_TOKENS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "agent_max_tokens must be between " + MIN_AGENT_MAX_TOKENS + " and " + MAX_AGENT_MAX_TOKENS + ".");
        }
        return parsed;
    }

    private Map<String, Object> conversationDto(PlaygroundConversationEntity entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entity.getId());
        dto.put("workflow_id", entity.getWorkflowId());
        dto.put("title", entity.getTitle());
        dto.put("created_at", iso(entity.getCreatedAt()));
        dto.put("updated_at", iso(entity.getUpdatedAt()));
        return dto;
    }

    private Map<String, Object> messageDto(PlaygroundMessageEntity entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entity.getId());
        dto.put("conversation_id", entity.getConversationId());
        dto.put("role", entity.getRole());
        dto.put("content", entity.getContent());
        dto.put("agent_name", entity.getAgentName());
        dto.put("created_at", iso(entity.getCreatedAt()));
        return dto;
    }

    private Map<String, Object> template(String type, String label, String description, int requiredAgentCount) {
        return Map.of(
                "type", type,
                "label", label,
                "description", description,
                "required_agent_count", requiredAgentCount
        );
    }

    private Map<String, Object> trace(String type, String title, String detail, String nodeId, String runId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("node_id", nodeId);
        payload.put("run_id", runId);
        return Map.of(
                "type", type,
                "title", title,
                "detail", detail,
                "at", java.time.OffsetDateTime.now().toString(),
                "payload", payload
        );
    }

    private String typeNode(String workflowType) {
        return switch (workflowType) {
            case "planner_executor" -> "planner_core";
            case "supervisor_dynamic" -> "supervisor_intake";
            case "peer_handoff" -> "first_owner_router";
            case "single_agent_chat" -> "single_agent";
            default -> "router";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "null";
        }
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> stringList(Object value) {
        return list(value).stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((k, v) -> normalized.put(String.valueOf(k), v));
                result.add(normalized);
            }
        }
        return result;
    }

    private List<Map<String, Object>> sanitizeEphemeralAgents(Object value) {
        List<Map<String, Object>> source = mapList(value);
        if (source.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source) {
            String id = text(item.get("id"), "");
            String name = text(item.get("name"), "");
            String model = text(item.get("model"), "");
            if (!id.startsWith("ephemeral:") || name.isBlank() || model.isBlank()) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("id", id);
            normalized.put("name", name);
            normalized.put("model", model);
            normalized.put("description", text(item.get("description"), ""));
            normalized.put("system_prompt", text(item.get("system_prompt"), ""));
            normalized.put("role", text(item.get("role"), "SPECIALIST"));
            normalized.put("max_tokens", normalizeAgentMaxTokens(item.get("max_tokens")));
            normalized.put("temperature", item.get("temperature"));
            normalized.put("planning_slot", Boolean.TRUE.equals(item.get("planning_slot"))
                    || Boolean.TRUE.equals(item.get("planningSlot")));
            normalized.put("ephemeral", true);
            result.add(normalized);
            if (result.size() >= 4) {
                break;
            }
        }
        return result;
    }

    private List<Map<String, Object>> sanitizeContextMessages(Object value) {
        List<Map<String, Object>> source = mapList(value);
        if (source.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source) {
            String role = text(item.get("role"), "user").toLowerCase();
            if (!role.equals("user") && !role.equals("assistant")) {
                continue;
            }
            String content = text(item.get("content"), "");
            if (content.isBlank()) {
                continue;
            }
            if (content.length() > 600) {
                content = content.substring(0, 600);
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("role", role);
            normalized.put("content", content);
            normalized.put("created_at", text(item.get("createdAt"), text(item.get("created_at"), "")));
            result.add(normalized);
            if (result.size() >= 4) {
                break;
            }
        }
        return result;
    }

    private List<Object> list(Object value) {
        if (value instanceof List<?> source) {
            return new ArrayList<>(source);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new HashMap<>();
    }

    private String requireText(Object value, String message) {
        String text = text(value, "");
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return text;
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String iso(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
