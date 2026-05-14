package com.adlin.orin.modules.conversation.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.conversation.dto.*;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.entity.ConversationLog;
import com.adlin.orin.modules.conversation.repository.AgentChatSessionRepository;
import com.adlin.orin.modules.conversation.strategy.ToolCallingCapabilityDetector;
import com.adlin.orin.modules.conversation.strategy.ToolCallingKbStrategy;
import com.adlin.orin.modules.skill.component.AiEngineMcpClient;
import com.adlin.orin.modules.conversation.dto.tooling.EffectiveToolBinding;
import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogItemDto;
import com.adlin.orin.modules.conversation.tool.*;
import com.adlin.orin.modules.conversation.tooling.ToolBindingService;
import com.adlin.orin.modules.conversation.tooling.ToolExecutor;
import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeGraphRepository;
import com.adlin.orin.modules.knowledge.repository.GraphEntityRepository;
import com.adlin.orin.modules.knowledge.repository.GraphRelationRepository;
import com.adlin.orin.modules.knowledge.service.KnowledgeManageService;
import com.adlin.orin.modules.knowledge.service.KnowledgeGraphService;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.service.SkillService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.BiConsumer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentChatService {

    private final AgentChatSessionRepository sessionRepository;
    private final AgentManageService agentManageService;
    private final ConversationLogService conversationLogService;
    private final AuditLogService auditLogService;
    private final MetaKnowledgeService metaKnowledgeService;
    private final RetrievalService retrievalService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final GraphEntityRepository graphEntityRepository;
    private final GraphRelationRepository graphRelationRepository;
    private final KnowledgeManageService knowledgeManageService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final McpServiceRepository mcpServiceRepository;
    private final AiEngineMcpClient aiEngineMcpClient;
    private final OllamaIntegrationService ollamaIntegrationService;
    private final ToolCallingCapabilityDetector toolCallingDetector;
    private final ToolBindingService toolBindingService;
    private final ToolExecutor toolExecutor;
    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    private KbStructureTool kbStructureTool;
    private KbSearchTool kbSearchTool;
    private KbRetrieveTool kbRetrieveTool;

    public AgentChatService(
            AgentChatSessionRepository sessionRepository,
            AgentManageService agentManageService,
            ConversationLogService conversationLogService,
            AuditLogService auditLogService,
            MetaKnowledgeService metaKnowledgeService,
            RetrievalService retrievalService,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeGraphRepository knowledgeGraphRepository,
            GraphEntityRepository graphEntityRepository,
            GraphRelationRepository graphRelationRepository,
            KnowledgeManageService knowledgeManageService,
            KnowledgeGraphService knowledgeGraphService,
            McpServiceRepository mcpServiceRepository,
            AiEngineMcpClient aiEngineMcpClient,
            OllamaIntegrationService ollamaIntegrationService,
            ToolCallingCapabilityDetector toolCallingDetector,
            ToolBindingService toolBindingService,
            ToolExecutor toolExecutor,
            SkillService skillService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.agentManageService = agentManageService;
        this.conversationLogService = conversationLogService;
        this.auditLogService = auditLogService;
        this.metaKnowledgeService = metaKnowledgeService;
        this.retrievalService = retrievalService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.knowledgeGraphRepository = knowledgeGraphRepository;
        this.graphEntityRepository = graphEntityRepository;
        this.graphRelationRepository = graphRelationRepository;
        this.knowledgeManageService = knowledgeManageService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.mcpServiceRepository = mcpServiceRepository;
        this.aiEngineMcpClient = aiEngineMcpClient;
        this.ollamaIntegrationService = ollamaIntegrationService;
        this.toolCallingDetector = toolCallingDetector;
        this.toolBindingService = toolBindingService;
        this.toolExecutor = toolExecutor;
        this.skillService = skillService;
        this.objectMapper = objectMapper;
        // Initialize tools
        this.kbStructureTool = new KbStructureTool(knowledgeBaseRepository, documentRepository);
        this.kbSearchTool = new KbSearchTool(retrievalService);
        this.kbRetrieveTool = new KbRetrieveTool();
    }

    /**
     * 创建新会话
     */
    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        
        AgentChatSession session = new AgentChatSession();
        session.setSessionId(sessionId);
        session.setAgentId(request.getAgentId());
        session.setTitle(request.getTitle() != null ? request.getTitle() : "新会话");
        session.setHistory("[]");
        
        sessionRepository.save(session);
        
        SessionResponse response = new SessionResponse();
        response.setId(sessionId);
        response.setAgentId(request.getAgentId());
        response.setTitle(session.getTitle());
        response.setCreatedAt(session.getCreatedAt().toString());
        
        return response;
    }

    /**
     * 获取会话列表
     */
    public List<SessionResponse> listSessions(String agentId) {
        List<AgentChatSession> sessions = sessionRepository.findByAgentIdOrderByUpdatedAtDesc(agentId);
        
        return sessions.stream().map(s -> {
            SessionResponse r = new SessionResponse();
            r.setId(s.getSessionId());
            r.setAgentId(s.getAgentId());
            r.setTitle(s.getTitle());
            r.setCreatedAt(s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
            return r;
        }).collect(Collectors.toList());
    }

    /**
     * 获取会话详情
     */
    public Map<String, Object> getSession(String sessionId) {
        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", session.getSessionId());
        result.put("agentId", session.getAgentId());
        result.put("title", session.getTitle());
        result.put("createdAt", session.getCreatedAt());
        
        // 解析历史消息
        try {
            List<Map<String, Object>> messages = objectMapper.readValue(session.getHistory(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            result.put("messages", messages);
        } catch (Exception e) {
            result.put("messages", new ArrayList<>());
        }
        
        // 获取附加的知识库
        result.put("attachedKbs", session.getAttachedKbIds());
        result.put("kbDocFilters", session.getKbDocFilters());

        return result;
    }

    /**
     * 覆盖保存前端编排类消息历史。
     * 用于统一对话页中的协作运行，避免绕过 Agent chat API 后历史丢失。
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void saveMessageHistory(String sessionId, Map<String, Object> body) {
        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        Object rawMessages = body != null ? body.get("messages") : null;
        if (!(rawMessages instanceof List<?> rawList)) {
            throw new IllegalArgumentException("messages must be an array");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> message = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> {
                    if (key != null) {
                        message.put(String.valueOf(key), value);
                    }
                });
                String role = String.valueOf(message.getOrDefault("role", "")).trim();
                if (role.equals("user") || role.equals("assistant") || role.equals("system")) {
                    messages.add(message);
                }
            }
        }

        try {
            session.setHistory(objectMapper.writeValueAsString(messages));
            session.setUpdatedAt(java.time.LocalDateTime.now());
            if (!messages.isEmpty()) {
                String firstUserText = messages.stream()
                        .filter(msg -> "user".equals(msg.get("role")))
                        .map(msg -> String.valueOf(msg.getOrDefault("content", "")).trim())
                        .filter(text -> !text.isBlank())
                        .findFirst()
                        .orElse("");
                if (!firstUserText.isBlank() && (session.getTitle() == null || session.getTitle().isBlank() || "新会话".equals(session.getTitle()))) {
                    session.setTitle(firstUserText.length() > 30 ? firstUserText.substring(0, 30) + "..." : firstUserText);
                }
            }
            sessionRepository.save(session);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化消息历史失败", e);
        }
    }

    /**
     * 发送消息
     */
    @Transactional
    public ChatMessageResponse sendMessage(String sessionId, ChatMessageRequest request) {
        return sendMessageInternal(sessionId, request, null);
    }

    @Transactional
    public ChatMessageResponse sendMessageStream(String sessionId, ChatMessageRequest request,
            BiConsumer<String, Object> eventPublisher) {
        return sendMessageInternal(sessionId, request, eventPublisher);
    }

    @Transactional
    private ChatMessageResponse sendMessageInternal(String sessionId, ChatMessageRequest request,
            BiConsumer<String, Object> eventPublisher) {
        long startedAtMs = System.currentTimeMillis();
        emitEvent(eventPublisher, "start", Map.of(
                "status", "running",
                "message", "开始处理请求"));

        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        // 提前检测模型是否支持 tool calling，决定 KB 工具链走哪条路
        AgentMetadata agentMetadata = agentManageService.getAgentMetadata(session.getAgentId());
        AgentAccessProfile agentProfile = agentManageService.getAgentAccessProfile(session.getAgentId());
        ToolCallingCapabilityDetector.ToolCallingDecision toolCallingDecision =
                toolCallingDetector.detect(session.getAgentId(), agentMetadata, agentProfile);
        boolean useToolCalling = toolCallingDecision.isSupported();
        RetrievalRuntimeConfig retrievalConfig = parseAgentRetrievalConfig(agentMetadata);

        // 解析历史
        List<Map<String, Object>> messages;
        try {
            messages = objectMapper.readValue(session.getHistory(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            messages = new ArrayList<>();
        }
        
        // 添加用户消息
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", request.getMessage());
        if (request.getFileId() != null && !request.getFileId().isBlank()) {
            userMsg.put("fileId", request.getFileId());
        }
        userMsg.put("createdAt", java.time.LocalDateTime.now().toString());
        messages.add(userMsg);

        EffectiveToolBinding effectiveBinding = toolBindingService.resolveEffectiveBinding(session, request);

        // 构建工具执行上下文
        ToolExecutionContext toolCtx = ToolExecutionContext.builder()
                .sessionId(sessionId)
                .agentId(session.getAgentId())
                .query(request.getMessage())
                .toolIds(effectiveBinding.getToolIds())
                .kbIds(effectiveBinding.getKbIds())
                .skillIds(effectiveBinding.getSkillIds())
                .mcpIds(effectiveBinding.getMcpIds())
                .kbDocFilters(request.getKbDocFilters())
                .retrievalTopK(retrievalConfig.topK())
                .retrievalThreshold(retrievalConfig.threshold())
                .retrievalAlpha(retrievalConfig.alpha())
                .retrievalEmbeddingModel(retrievalConfig.embeddingModel())
                .retrievalEnableRerank(retrievalConfig.enableRerank())
                .retrievalRerankModel(retrievalConfig.rerankModel())
                .traces(new ArrayList<>())
                .sharedState(new HashMap<>())
                .retrievedChunks(new ArrayList<>())
                .build();
        String conversationReferenceContext = buildConversationReferenceContext(request.getConversationContextMessages());
        if (!conversationReferenceContext.isBlank()) {
            toolCtx.putSharedState("conversationReferenceContext", conversationReferenceContext);
        }

        toolExecutor.applyBinding(toolCtx, useToolCalling, eventPublisher);

        // 执行工具链
        if (toolCtx.getKbIds() != null && !toolCtx.getKbIds().isEmpty()) {
            try {
                Map<String, Object> strategyDetail = new LinkedHashMap<>();
                strategyDetail.put("mode", useToolCalling ? "tool_calling" : "context_injection");
                strategyDetail.put("decisionSource", toolCallingDecision.getSource() != null ? toolCallingDecision.getSource() : "");
                strategyDetail.put("decisionReason", toolCallingDecision.getReason() != null ? toolCallingDecision.getReason() : "");
                strategyDetail.put("toolCallingOverride", agentMetadata != null ? agentMetadata.getToolCallingOverride() : null);
                ChatMessageResponse.ToolTrace strategyTrace = ChatMessageResponse.ToolTrace.builder()
                        .type("KB_STRATEGY")
                        .kbId("multiple")
                        .message(useToolCalling
                                ? "检索策略：模型工具调用（模型自主检索知识库）"
                                : "检索策略：上下文附加（先检索再注入上下文）")
                        .status("success")
                        .durationMs(0L)
                        .detail(strategyDetail)
                        .build();
                toolCtx.addTrace(strategyTrace);
                emitEvent(eventPublisher, "trace", strategyTrace);

                emitEvent(eventPublisher, "progress", Map.of(
                        "step", "KB_STRUCTURE",
                        "message", "正在检查知识库结构..."));
                emitEvent(eventPublisher, "trace", ChatMessageResponse.ToolTrace.builder()
                        .type("KB_STRUCTURE")
                        .kbId("multiple")
                        .message("知识库结构检查中...")
                        .status("running")
                        .durationMs(0L)
                        .build());
                ChatMessageResponse.ToolTrace structureTrace = kbStructureTool.execute(toolCtx);
                toolCtx.addTrace(structureTrace);
                emitEvent(eventPublisher, "trace", structureTrace);

                // tool calling 模型自己负责检索，跳过预检索步骤
                if (!useToolCalling) {
                    emitEvent(eventPublisher, "progress", Map.of(
                            "step", "KB_SEARCH",
                            "message", "正在检索相关知识..."));
                    emitEvent(eventPublisher, "trace", ChatMessageResponse.ToolTrace.builder()
                            .type("KB_SEARCH")
                            .kbId("multiple")
                            .message("知识检索中...")
                            .status("running")
                            .durationMs(0L)
                            .build());
                    ChatMessageResponse.ToolTrace searchTrace = kbSearchTool.execute(toolCtx);
                    toolCtx.addTrace(searchTrace);
                    emitEvent(eventPublisher, "trace", searchTrace);

                    emitEvent(eventPublisher, "progress", Map.of(
                            "step", "KB_RETRIEVE",
                            "message", "正在组装上下文..."));
                    emitEvent(eventPublisher, "trace", ChatMessageResponse.ToolTrace.builder()
                            .type("KB_RETRIEVE")
                            .kbId("multiple")
                            .message("上下文组装中...")
                            .status("running")
                            .durationMs(0L)
                            .build());
                    ChatMessageResponse.ToolTrace retrieveTrace = kbRetrieveTool.execute(toolCtx);
                    toolCtx.addTrace(retrieveTrace);
                    emitEvent(eventPublisher, "trace", retrieveTrace);
                } else {
                    ChatMessageResponse.ToolTrace delegatedTrace = ChatMessageResponse.ToolTrace.builder()
                            .type("KB_PIPELINE")
                            .kbId("multiple")
                            .message("已跳过预检索步骤：由模型在回答过程中按需调用知识库工具")
                            .status("success")
                            .durationMs(0L)
                            .detail(Map.of(
                                    "delegatedToModel", true,
                                    "expectedTools", List.of("query_kb", "read_document")))
                            .build();
                    toolCtx.addTrace(delegatedTrace);
                    emitEvent(eventPublisher, "trace", delegatedTrace);
                }
            } catch (Exception e) {
                log.warn("工具链执行失败: {}", e.getMessage());
                ChatMessageResponse.ToolTrace errorTrace = ChatMessageResponse.ToolTrace.builder()
                        .type("KB_PIPELINE")
                        .kbId("multiple")
                        .message("知识库检索链路异常，已跳过知识上下文")
                        .status("error")
                        .durationMs(0L)
                        .detail(Map.of("error", e.getMessage()))
                        .build();
                toolCtx.addTrace(errorTrace);
                emitEvent(eventPublisher, "trace", errorTrace);
            }
        }

        // 绑定 MCP 服务到本轮对话上下文
        if (toolCtx.getMcpIds() != null && !toolCtx.getMcpIds().isEmpty()) {
            runMcpBinding(toolCtx, eventPublisher);
        }

        // 从工具上下文构建检索结果
        List<RetrievedChunk> retrievedChunks = new ArrayList<>();
        String context = "";
        for (Object chunkObj : toolCtx.getRetrievedChunks()) {
            if (chunkObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> chunkMap = (Map<String, Object>) chunkObj;
                RetrievedChunk chunk = new RetrievedChunk();
                chunk.setKbId((String) chunkMap.get("kbId"));
                chunk.setContent((String) chunkMap.get("content"));
                chunk.setScore((Double) chunkMap.get("score"));
                chunk.setDocId((String) chunkMap.get("docId"));
                chunk.setDocName((String) chunkMap.get("title"));
                chunk.setSource((String) chunkMap.get("source"));
                retrievedChunks.add(chunk);
                context += "\n\n" + chunk.getContent();
            }
        }
        emitEvent(eventPublisher, "retrieved", Map.of(
                "count", retrievedChunks.size(),
                "retrievedChunks", retrievedChunks));

        // 调用智能体
        log.info("检索上下文内容: retrievedChunks.size={}, context长度={}, context前200字={}",
                retrievedChunks.size(),
                context != null ? context.length() : 0,
                context != null && context.length() > 200 ? context.substring(0, 200) : context);

        // 附加了知识库但未检索到任何内容时，给前端一个明确提醒（避免”静默失败”）。
        // tool calling 模式下检索由模型自主发起，retrievedChunks 为空属于正常情况。
        if (!useToolCalling && toolCtx.getKbIds() != null && !toolCtx.getKbIds().isEmpty() && retrievedChunks.isEmpty()) {
            ChatMessageResponse.ToolTrace hintTrace = ChatMessageResponse.ToolTrace.builder()
                    .type("KB_HINT")
                    .kbId("multiple")
                    .message("未检索到可用知识：可能是向量服务不可用、文档未解析/未向量化，或查询词未命中。")
                    .status("warning")
                    .durationMs(0L)
                    .detail(Map.of(
                            "kbIds", toolCtx.getKbIds(),
                            "kbDocFilters", request.getKbDocFilters() != null ? request.getKbDocFilters() : Collections.emptyMap(),
                            "retrievedCount", 0))
                    .build();
            toolCtx.addTrace(hintTrace);
            emitEvent(eventPublisher, "trace", hintTrace);
        }

        emitEvent(eventPublisher, "progress", Map.of(
                "step", "MODEL_CALL",
                "message", "正在生成回复..."));
        startModelReasoningTrace(toolCtx, eventPublisher, request.getEnableThinking());
        Map<String, Object> agentResult = callAgent(
                session.getAgentId(),
                request.getMessage(),
                request.getFileId(),
                context,
                toolCtx,
                eventPublisher,
                useToolCalling,
                request.getEnableThinking(),
                request.getThinkingBudget(),
                request.getMaxTokens());
        completeModelReasoningTrace(agentResult, toolCtx, eventPublisher);
        String aiResponse = (String) agentResult.get("content");

        // 添加 AI 响应
        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", aiResponse);
        assistantMsg.put("retrievedChunks", retrievedChunks);
        assistantMsg.put("toolTraces", toolCtx.getTraces());
        assistantMsg.put("promptTokens", (Integer) agentResult.getOrDefault("promptTokens", 0));
        assistantMsg.put("completionTokens", (Integer) agentResult.getOrDefault("completionTokens", 0));
        assistantMsg.put("model", (String) agentResult.getOrDefault("model", ""));
        assistantMsg.put("provider", (String) agentResult.getOrDefault("provider", ""));
        assistantMsg.put("createdAt", java.time.LocalDateTime.now().toString());
        messages.add(assistantMsg);

        // 保存历史
        try {
            session.setHistory(objectMapper.writeValueAsString(messages));
        } catch (JsonProcessingException e) {
            log.error("序列化消息历史失败", e);
        }
        sessionRepository.save(session);

        // 返回响应
        ChatMessageResponse response = new ChatMessageResponse();
        response.setContent(aiResponse);
        response.setRetrievedChunks(retrievedChunks);
        response.setToolTraces(toolCtx.getTraces());
        response.setPromptTokens((Integer) agentResult.getOrDefault("promptTokens", 0));
        response.setCompletionTokens((Integer) agentResult.getOrDefault("completionTokens", 0));
        response.setModel((String) agentResult.getOrDefault("model", ""));
        response.setProvider((String) agentResult.getOrDefault("provider", ""));
        response.setCreatedAt(java.time.LocalDateTime.now().toString());

        int promptTokens = (Integer) agentResult.getOrDefault("promptTokens", 0);
        int completionTokens = (Integer) agentResult.getOrDefault("completionTokens", 0);
        int totalTokens = (Integer) agentResult.getOrDefault("totalTokens", promptTokens + completionTokens);
        long responseTime = System.currentTimeMillis() - startedAtMs;
        boolean success = !(aiResponse != null && (
                aiResponse.startsWith("智能体调用失败")
                || aiResponse.startsWith("模型调用失败")
                || aiResponse.startsWith("智能体返回为空")));
        String modelName = (String) agentResult.getOrDefault("model",
                agentMetadata != null ? agentMetadata.getModelName() : "");

        ConversationLog conversationLog = ConversationLog.builder()
                .conversationId(sessionId)
                .agentId(session.getAgentId())
                .userId(null)
                .model(modelName)
                .query(request.getMessage())
                .response(aiResponse)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .responseTime(responseTime)
                .success(success)
                .errorMessage(success ? null : aiResponse)
                .build();
        conversationLogService.log(conversationLog);

        try {
            Map<String, Object> reqMeta = new HashMap<>();
            reqMeta.put("sessionId", sessionId);
            reqMeta.put("agentId", session.getAgentId());
            reqMeta.put("message", request.getMessage());
            reqMeta.put("toolIds", toolCtx.getToolIds() != null ? toolCtx.getToolIds() : Collections.emptyList());
            reqMeta.put("kbIds", toolCtx.getKbIds() != null ? toolCtx.getKbIds() : Collections.emptyList());
            reqMeta.put("kbDocFilters", request.getKbDocFilters() != null ? request.getKbDocFilters() : Collections.emptyMap());
            reqMeta.put("skillIds", toolCtx.getSkillIds() != null ? toolCtx.getSkillIds() : Collections.emptyList());
            reqMeta.put("mcpIds", toolCtx.getMcpIds() != null ? toolCtx.getMcpIds() : Collections.emptyList());
            reqMeta.put("retrievedCount", retrievedChunks.size());
            reqMeta.put("toolTraceCount", toolCtx.getTraces() != null ? toolCtx.getTraces().size() : 0);

            String requestParams = objectMapper.writeValueAsString(reqMeta);
            String responseContent = aiResponse;
            String modelForAudit = modelName != null ? modelName : "";

            auditLogService.logApiCall(
                    "SYSTEM",
                    null,
                    session.getAgentId(),
                    "AGENT_CHAT",
                    "/api/v1/agents/chat/sessions/" + sessionId + "/messages",
                    "POST",
                    modelForAudit,
                    null,
                    "ORIN",
                    requestParams,
                    responseContent,
                    success ? 200 : 500,
                    responseTime,
                    promptTokens,
                    completionTokens,
                    0.0,
                    success,
                    success ? null : aiResponse,
                    null,
                    sessionId);
        } catch (Exception e) {
            log.warn("写入审计日志失败: {}", e.getMessage());
        }

        emitEvent(eventPublisher, "done", response);
        return response;
    }

    private void runMcpBinding(ToolExecutionContext toolCtx, BiConsumer<String, Object> eventPublisher) {
        List<Long> mcpIds = toolCtx.getMcpIds();
        if (mcpIds == null || mcpIds.isEmpty()) {
            return;
        }

        emitEvent(eventPublisher, "progress", Map.of(
                "step", "MCP_BIND",
                "message", "正在绑定 MCP 服务..."));

        StringBuilder mcpContext = new StringBuilder();
        for (Long mcpId : mcpIds) {
            Optional<McpService> serviceOpt = mcpServiceRepository.findById(mcpId);
            if (serviceOpt.isEmpty()) {
                ChatMessageResponse.ToolTrace trace = ChatMessageResponse.ToolTrace.builder()
                        .type("MCP_BIND")
                        .kbId(String.valueOf(mcpId))
                        .message("MCP 服务不存在，已跳过")
                        .status("error")
                        .durationMs(0L)
                        .build();
                toolCtx.addTrace(trace);
                emitEvent(eventPublisher, "trace", trace);
                continue;
            }

            McpService service = serviceOpt.get();
            if (!Boolean.TRUE.equals(service.getEnabled())) {
                ChatMessageResponse.ToolTrace trace = ChatMessageResponse.ToolTrace.builder()
                        .type("MCP_BIND")
                        .kbId(String.valueOf(service.getId()))
                        .message("MCP 服务已禁用，已跳过")
                        .status("warning")
                        .durationMs(0L)
                        .detail(Map.of("name", service.getName()))
                        .build();
                toolCtx.addTrace(trace);
                emitEvent(eventPublisher, "trace", trace);
                continue;
            }

            if (mcpContext.length() > 0) {
                mcpContext.append("\n");
            }
            mcpContext.append("- ")
                    .append(service.getName())
                    .append(" (").append(service.getType()).append(")");
            if (service.getDescription() != null && !service.getDescription().isBlank()) {
                mcpContext.append(": ").append(service.getDescription());
            }

            ChatMessageResponse.ToolTrace trace = ChatMessageResponse.ToolTrace.builder()
                    .type("MCP_BIND")
                    .kbId(String.valueOf(service.getId()))
                    .message("MCP 服务已接入本轮对话上下文")
                    .status("success")
                    .durationMs(0L)
                    .detail(Map.of(
                            "name", service.getName(),
                            "type", service.getType(),
                            "toolKey", service.getToolKey() != null ? service.getToolKey() : "custom"))
                    .build();
            toolCtx.addTrace(trace);
            emitEvent(eventPublisher, "trace", trace);
        }

        if (mcpContext.length() > 0) {
            toolCtx.putSharedState("mcpContext", mcpContext.toString());
        }
    }

    private void emitEvent(BiConsumer<String, Object> eventPublisher, String eventType, Object payload) {
        if (eventPublisher != null) {
            try {
                eventPublisher.accept(eventType, payload);
            } catch (Exception e) {
                log.warn("发送 SSE 事件失败: type={}, err={}", eventType, e.getMessage());
            }
        }
    }

    /**
     * 调用智能体，返回内容和usage信息
     */
    private Map<String, Object> callAgent(String agentId, String userMessage, String fileId, String context,
                             ToolExecutionContext toolCtx,
                             BiConsumer<String, Object> eventPublisher,
                             boolean useToolCalling,
                             Boolean enableThinking,
                             Integer thinkingBudget,
                             Integer maxTokensOverride) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", "");
        result.put("promptTokens", 0);
        result.put("completionTokens", 0);
        result.put("model", "");
        result.put("provider", "");
        result.put("modelThinkingRequested", Boolean.TRUE.equals(enableThinking));

        try {
            AgentMetadata metadata = agentManageService.getAgentMetadata(agentId);
            AgentAccessProfile profile = agentManageService.getAgentAccessProfile(agentId);

            // ── 支持 tool calling 的模型：让模型自主决定检索/读全文/调用绑定工具 ──
            @SuppressWarnings("unchecked")
            List<ToolCatalogItemDto> fnCallTools = toolCtx != null
                    ? (List<ToolCatalogItemDto>) toolCtx.getSharedState(
                            ToolExecutor.SHARED_STATE_FUNCTION_CALL_TOOLS)
                    : null;
            boolean hasFnCallTools = fnCallTools != null && !fnCallTools.isEmpty();
            boolean hasKbs = toolCtx != null
                    && toolCtx.getKbIds() != null && !toolCtx.getKbIds().isEmpty();
            boolean hasNonKbFnTools = hasFnCallTools && fnCallTools.stream()
                    .anyMatch(tool -> !("BUILTIN_KB".equalsIgnoreCase(tool.getCategory())));

            if (toolCtx != null && useToolCalling && (hasKbs || hasNonKbFnTools)) {
                final ToolExecutionContext ctx = toolCtx;
                String baseSystemPrompt = metaKnowledgeService.assembleSystemPrompt(agentId);
                String conversationReferenceContext = toolCtx != null
                        ? (String) toolCtx.getSharedState("conversationReferenceContext")
                        : null;
                if (conversationReferenceContext != null && !conversationReferenceContext.isBlank()) {
                    baseSystemPrompt = appendConversationReferenceContext(baseSystemPrompt, conversationReferenceContext);
                }
                double temperature = metadata != null && metadata.getTemperature() != null
                        ? metadata.getTemperature() : 0.7;
                int maxTokens = metadata != null && metadata.getMaxTokens() != null
                        ? metadata.getMaxTokens() : 2048;

                ToolCallingKbStrategy strategy = new ToolCallingKbStrategy(
                        ollamaIntegrationService, retrievalService, documentRepository,
                        knowledgeBaseRepository, knowledgeGraphRepository, graphEntityRepository,
                        graphRelationRepository,
                        knowledgeManageService, knowledgeGraphService, skillService, mcpServiceRepository,
                        aiEngineMcpClient, objectMapper);
                strategy.configureRetrieval(
                        ctx.getRetrievalTopK(),
                        ctx.getRetrievalThreshold(),
                        ctx.getRetrievalAlpha(),
                        ctx.getRetrievalEmbeddingModel(),
                        ctx.getRetrievalEnableRerank(),
                        ctx.getRetrievalRerankModel());

                List<String> kbIdsForStrategy = hasKbs ? ctx.getKbIds() : Collections.emptyList();
                List<ToolCatalogItemDto> boundForStrategy = hasFnCallTools
                        ? fnCallTools : Collections.emptyList();

                log.info("tool-calling RAG: agentId={}, kbIds={}, boundFnTools={}",
                        agentId, kbIdsForStrategy,
                        boundForStrategy.stream().map(ToolCatalogItemDto::getToolId).toList());
                return strategy.execute(
                        profile.getEndpointUrl(), profile.getApiKey(),
                        metadata != null ? metadata.getModelName() : null,
                        baseSystemPrompt, userMessage, kbIdsForStrategy,
                        boundForStrategy,
                        temperature, maxTokens,
                        trace -> {
                            ctx.addTrace(trace);
                            emitEvent(eventPublisher, "trace", trace);
                        });
            }

            // ── 云端 provider：context injection（将检索结果注入 system prompt）──
            String baseSystemPrompt = metaKnowledgeService.assembleSystemPrompt(agentId);
            String kbStructurePreamble = buildKbStructurePreamble(toolCtx);
            String mcpContext = toolCtx != null ? (String) toolCtx.getSharedState("mcpContext") : null;
            String toolContext = toolCtx != null ? (String) toolCtx.getSharedState("toolContext") : null;
            String conversationReferenceContext = toolCtx != null
                    ? (String) toolCtx.getSharedState("conversationReferenceContext")
                    : null;

            StringBuilder systemSuffix = new StringBuilder();
            if (conversationReferenceContext != null && !conversationReferenceContext.isBlank()) {
                systemSuffix.append("\n\n").append(conversationReferenceContext);
            }
            if (kbStructurePreamble != null && !kbStructurePreamble.isBlank()) {
                systemSuffix.append("\n\n").append(kbStructurePreamble);
            }
            if (context != null && !context.isEmpty()) {
                systemSuffix.append("\n\n以下是与用户问题相关的知识库检索内容，请直接基于此回答，无需说明内容来源：\n").append(context);
            }
            if (mcpContext != null && !mcpContext.isBlank()) {
                systemSuffix.append("\n\n可用 MCP 服务:\n").append(mcpContext);
            }
            if (toolContext != null && !toolContext.isBlank()) {
                systemSuffix.append("\n\n可用工具目录:\n").append(toolContext);
            }

            String extendedSystemPrompt = systemSuffix.length() > 0
                    ? baseSystemPrompt + systemSuffix
                    : null;

            log.info("云端 provider context-injection RAG: agentId={}, systemSuffix长度={}, userMessage长度={}",
                    agentId, systemSuffix.length(), userMessage.length());

            // 调用 AgentManageService 的 chat 方法；有知识库/MCP 上下文时通过 overrideSystemPrompt 注入
            Optional<Object> response = extendedSystemPrompt != null
                    ? agentManageService.chat(agentId, userMessage, fileId, extendedSystemPrompt, toolCtx.getSessionId(),
                            enableThinking, thinkingBudget, maxTokensOverride)
                    : agentManageService.chat(agentId, userMessage, fileId, null, toolCtx.getSessionId(),
                            enableThinking, thinkingBudget, maxTokensOverride);

            if (response.isPresent()) {
                Object resp = response.get();
                if (resp instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> respMap = (Map<String, Object>) resp;
                    log.info("智能体响应(map) keys={}", respMap.keySet());
                    String content = extractContent(respMap);
                    String errorText = extractErrorText(respMap);

                    // 有检索上下文但未拿到正文时，自动回退一次“无上下文对话”，避免静默空白。
                    if ((content == null || content.isBlank())
                            && (errorText == null || errorText.isBlank())
                            && context != null && !context.isBlank()) {
                        log.warn("检索上下文调用未返回正文，触发无上下文回退重试: agentId={}", agentId);
                        try {
                            Optional<Object> fallbackResp = agentManageService.chat(agentId, userMessage, fileId);
                            if (fallbackResp.isEmpty()) {
                                fallbackResp = agentManageService.chat(agentId, userMessage, fileId, null, toolCtx.getSessionId());
                            }
                            if (fallbackResp.isPresent() && fallbackResp.get() instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> fallbackMap = (Map<String, Object>) fallbackResp.get();
                                String fallbackContent = extractContent(fallbackMap);
                                String fallbackError = extractErrorText(fallbackMap);
                                if (fallbackContent != null && !fallbackContent.isBlank()) {
                                    content = fallbackContent;
                                    // 回退成功时使用回退请求的元信息
                                    respMap = fallbackMap;
                                    log.info("无上下文回退重试成功: agentId={}", agentId);
                                } else if (fallbackError != null && !fallbackError.isBlank()) {
                                    errorText = fallbackError;
                                }
                            } else if (fallbackResp.isPresent() && fallbackResp.get() instanceof String fallbackText) {
                                if (!fallbackText.isBlank()) {
                                    content = fallbackText;
                                    log.info("无上下文回退重试成功(字符串响应): agentId={}", agentId);
                                }
                            }
                        } catch (Exception fallbackEx) {
                            log.warn("无上下文回退重试失败: {}", fallbackEx.getMessage());
                        }
                    }

                    if ((content == null || content.isBlank())
                            && errorText != null && !errorText.isBlank()) {
                        content = "模型调用失败: " + errorText;
                    }
                    if (content == null || content.isBlank()) {
                        boolean hasKbContext = context != null && !context.isBlank();
                        content = hasKbContext
                                ? "检索流程已完成，但模型未返回正文。"
                                : "模型未返回正文，请重试。";
                    }

                    Map<String, Integer> usage = extractUsageFromAgentResponse(respMap);
                    String reasoning = extractReasoningText(respMap);
                    result.put("content", content);
                    result.put("promptTokens", usage.get("prompt"));
                    result.put("completionTokens", usage.get("completion"));
                    result.put("totalTokens", usage.get("total"));
                    result.put("model", respMap.getOrDefault("model", ""));
                    result.put("provider", respMap.getOrDefault("provider", ""));
                    result.put("modelReasoningPresent", reasoning != null && !reasoning.isBlank());
                    result.put("modelReasoningChars", reasoning != null ? reasoning.length() : 0);
                    result.put("modelReasoningRaw", reasoning != null ? reasoning : "");
                    result.put("visibleReasoningSummary", buildVisibleReasoningSummary(content, reasoning));
                } else if (resp instanceof String) {
                    result.put("content", (String) resp);
                } else {
                    result.put("content", resp.toString());
                }
            } else {
                result.put("content", "智能体返回为空");
            }

        } catch (Exception e) {
            log.error("调用智能体失败", e);
            result.put("content", "智能体调用失败: " + e.getMessage());
        }

        return result;
    }

    private void startModelReasoningTrace(ToolExecutionContext toolCtx, BiConsumer<String, Object> eventPublisher,
                                          Boolean enableThinking) {
        if (toolCtx == null) {
            return;
        }

        boolean thinkingRequested = Boolean.TRUE.equals(enableThinking);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("startedAt", System.currentTimeMillis());
        detail.put("rawReasoningVisible", false);
        detail.put("thinkingRequested", thinkingRequested);

        ChatMessageResponse.ToolTrace trace = ChatMessageResponse.ToolTrace.builder()
                .type("MODEL_REASONING")
                .kbId("model")
                .message(thinkingRequested ? "深度思考已开启，模型正在推理并生成回复..." : "模型正在生成回复...")
                .status("running")
                .durationMs(0L)
                .detail(detail)
                .build();
        upsertModelReasoningTrace(toolCtx, trace);
        emitEvent(eventPublisher, "trace", trace);
    }

    private void completeModelReasoningTrace(Map<String, Object> agentResult, ToolExecutionContext toolCtx,
                                             BiConsumer<String, Object> eventPublisher) {
        if (toolCtx == null) {
            return;
        }

        boolean reasoningPresent = Boolean.TRUE.equals(agentResult.get("modelReasoningPresent"));
        boolean thinkingRequested = Boolean.TRUE.equals(agentResult.get("modelThinkingRequested"));
        int reasoningChars = toInt(agentResult.get("modelReasoningChars"));
        String rawReasoning = String.valueOf(agentResult.getOrDefault("modelReasoningRaw", ""));
        String visibleSummary = String.valueOf(agentResult.getOrDefault("visibleReasoningSummary", ""));
        long startedAt = extractModelReasoningStartedAt(toolCtx);
        long durationMs = startedAt > 0 ? Math.max(0L, System.currentTimeMillis() - startedAt) : 0L;
        boolean rawReasoningVisible = reasoningPresent && !rawReasoning.isBlank();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reasoningPresent", reasoningPresent);
        detail.put("reasoningChars", reasoningChars);
        detail.put("rawReasoningVisible", rawReasoningVisible);
        detail.put("thinkingRequested", thinkingRequested);
        detail.put("modelReasoningOccurred", reasoningPresent);
        if (!visibleSummary.isBlank()) {
            detail.put("visibleSummary", visibleSummary);
        }
        if (rawReasoningVisible) {
            detail.put("reason-context", rawReasoning);
        }
        detail.put("note", reasoningPresent
                ? (rawReasoningVisible
                        ? "已透传 provider 原始思考全文（reason-context）。"
                        : "内部推理已折叠为可见摘要，不直接展示原始思考全文。")
                : (thinkingRequested
                        ? "已请求深度思考，但当前模型或提供方未返回可展示的思考字段。"
                        : "当前模型或提供方未返回可展示的思考字段。"));

        ChatMessageResponse.ToolTrace trace = ChatMessageResponse.ToolTrace.builder()
                .type("MODEL_REASONING")
                .kbId("model")
                .message(rawReasoningVisible
                        ? "模型完成了内部思考，已返回 provider 原始思考全文。"
                        : (reasoningPresent
                        ? "模型完成了内部思考，已生成可见思路摘要。"
                        : (thinkingRequested
                                ? "已请求深度思考，但提供方未返回思考字段；模型回复已完成。"
                                : "模型已完成生成；当前提供方未返回可展示的思考字段。")))
                .status(reasoningPresent ? "success" : (thinkingRequested ? "warning" : "success"))
                .durationMs(durationMs)
                .detail(detail)
                .build();
        upsertModelReasoningTrace(toolCtx, trace);
        emitEvent(eventPublisher, "trace", trace);
    }

    private long extractModelReasoningStartedAt(ToolExecutionContext toolCtx) {
        if (toolCtx == null || toolCtx.getTraces() == null) {
            return 0L;
        }
        for (ChatMessageResponse.ToolTrace trace : toolCtx.getTraces()) {
            if (!"MODEL_REASONING".equals(trace.getType()) || !(trace.getDetail() instanceof Map<?, ?> detail)) {
                continue;
            }
            Object startedAt = detail.get("startedAt");
            if (startedAt instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
    }

    private void upsertModelReasoningTrace(ToolExecutionContext toolCtx, ChatMessageResponse.ToolTrace trace) {
        if (toolCtx.getTraces() == null) {
            toolCtx.setTraces(new ArrayList<>());
        }
        List<ChatMessageResponse.ToolTrace> traces = toolCtx.getTraces();
        for (int i = 0; i < traces.size(); i++) {
            if ("MODEL_REASONING".equals(traces.get(i).getType())) {
                traces.set(i, trace);
                return;
            }
        }
        traces.add(trace);
    }

    private String extractReasoningText(Map<String, Object> respMap) {
        if (respMap == null || respMap.isEmpty()) {
            return "";
        }

        Object topReasoning = respMap.get("reasoning");
        if (topReasoning instanceof String s && !s.isBlank()) {
            return s;
        }
        Object topReasoningContent = respMap.get("reasoning_content");
        if (topReasoningContent instanceof String s && !s.isBlank()) {
            return s;
        }

        Object choicesObj = respMap.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty() && choices.get(0) instanceof Map<?, ?> choiceMap) {
            String fromMessage = extractReasoningFromMessageObject(choiceMap.get("message"));
            if (!fromMessage.isBlank()) {
                return fromMessage;
            }
            String fromDelta = extractReasoningFromMessageObject(choiceMap.get("delta"));
            if (!fromDelta.isBlank()) {
                return fromDelta;
            }
        }

        Object dataObj = respMap.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            Object dataReasoning = dataMap.get("reasoning");
            if (dataReasoning instanceof String s && !s.isBlank()) {
                return s;
            }
            Object dataReasoningContent = dataMap.get("reasoning_content");
            if (dataReasoningContent instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        return "";
    }

    private String buildVisibleReasoningSummary(String content, String rawReasoning) {
        boolean reasoningPresent = rawReasoning != null && !rawReasoning.isBlank();
        String answer = content != null ? content.trim() : "";
        if (!reasoningPresent && answer.isBlank()) {
            return "";
        }

        List<String> bullets = new ArrayList<>();
        if (reasoningPresent) {
            bullets.add("模型返回了内部推理字段，ORIN 已确认本轮发生过模型内部推理。");
        } else {
            bullets.add("模型未返回可识别的内部推理字段，以下仅基于最终回答整理。");
        }

        List<String> answerSignals = summarizeAnswerShape(answer);
        if (!answerSignals.isEmpty()) {
            bullets.addAll(answerSignals);
        } else {
            bullets.add("模型完成问题理解、信息组织和答案生成，原始内部推理未直接展示。");
        }
        return String.join("\n", bullets);
    }

    private List<String> summarizeAnswerShape(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        String normalized = content
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .trim();
        List<String> lines = Arrays.stream(normalized.split("\\R+"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> line.length() >= 8)
                .limit(3)
                .toList();
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> bullets = new ArrayList<>();
        bullets.add("可见思路摘要基于最终回答整理，不逐字暴露内部推理。");
        for (String line : lines) {
            String cleaned = line
                    .replaceAll("^[-*\\d.、\\s]+", "")
                    .replaceAll("\\s+", " ");
            if (cleaned.length() > 80) {
                cleaned = cleaned.substring(0, 80) + "...";
            }
            bullets.add("回答围绕：" + cleaned);
        }
        return bullets;
    }

    private String extractReasoningFromMessageObject(Object messageObj) {
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return "";
        }
        Object reasoning = messageMap.get("reasoning");
        if (reasoning instanceof String s && !s.isBlank()) {
            return s;
        }
        Object reasoningContent = messageMap.get("reasoning_content");
        if (reasoningContent instanceof String s && !s.isBlank()) {
            return s;
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> extractUsageFromAgentResponse(Map<String, Object> respMap) {
        Map<String, Integer> usage = new HashMap<>();
        usage.put("prompt", 0);
        usage.put("completion", 0);
        usage.put("total", 0);

        if (respMap == null) {
            return usage;
        }

        mergeUsageFromMap(respMap, usage);

        Object dataObj = respMap.get("data");
        if (dataObj instanceof Map) {
            mergeUsageFromMap((Map<String, Object>) dataObj, usage);
        }

        if (usage.get("total") <= 0) {
            usage.put("total", usage.get("prompt") + usage.get("completion"));
        }
        return usage;
    }

    @SuppressWarnings("unchecked")
    private void mergeUsageFromMap(Map<String, Object> source, Map<String, Integer> usage) {
        if (source == null || usage == null) return;

        Object usageObj = source.get("usage");
        if (usageObj instanceof Map) {
            Map<String, Object> usageMap = (Map<String, Object>) usageObj;
            usage.put("prompt", firstNonZero(
                    usage.get("prompt"),
                    toInt(usageMap.get("prompt_tokens")),
                    toInt(usageMap.get("promptTokens")),
                    toInt(usageMap.get("prompt_eval_count"))));
            usage.put("completion", firstNonZero(
                    usage.get("completion"),
                    toInt(usageMap.get("completion_tokens")),
                    toInt(usageMap.get("completionTokens")),
                    toInt(usageMap.get("eval_count"))));
            usage.put("total", firstNonZero(
                    usage.get("total"),
                    toInt(usageMap.get("total_tokens")),
                    toInt(usageMap.get("totalTokens")),
                    toInt(usageMap.get("tokens"))));
        }

        usage.put("prompt", firstNonZero(
                usage.get("prompt"),
                toInt(source.get("promptTokens")),
                toInt(source.get("prompt_tokens")),
                toInt(source.get("prompt_eval_count"))));
        usage.put("completion", firstNonZero(
                usage.get("completion"),
                toInt(source.get("completionTokens")),
                toInt(source.get("completion_tokens")),
                toInt(source.get("eval_count"))));
        usage.put("total", firstNonZero(
                usage.get("total"),
                toInt(source.get("totalTokens")),
                toInt(source.get("total_tokens")),
                toInt(source.get("tokens"))));
    }

    private int firstNonZero(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private String buildKbStructurePreamble(ToolExecutionContext toolCtx) {
        if (toolCtx == null) return null;
        Boolean checked = (Boolean) toolCtx.getSharedState("kbStructureChecked");
        if (!Boolean.TRUE.equals(checked)) return null;

        Map<String, Object> detail = (Map<String, Object>) toolCtx.getSharedState("kbStructureDetail");
        if (detail == null || detail.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("知识库结构信息:\n");
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            Map<String, Object> kbInfo = (Map<String, Object>) entry.getValue();
            if (kbInfo.containsKey("error")) continue;
            String name = (String) kbInfo.getOrDefault("name", entry.getKey());
            Object docCount = kbInfo.get("documentCount");
            sb.append("- ").append(name).append(": ").append(docCount).append(" 个文档\n");
        }
        return sb.toString().trim();
    }

    private String buildConversationReferenceContext(List<Map<String, Object>> contextMessages) {
        if (contextMessages == null || contextMessages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        int usedChars = 0;
        for (Map<String, Object> item : contextMessages) {
            if (item == null || count >= 4 || usedChars >= 1800) {
                break;
            }
            String role = String.valueOf(item.getOrDefault("role", "")).trim();
            String content = String.valueOf(item.getOrDefault("content", "")).trim();
            if (content.isBlank()) {
                continue;
            }
            String label = "assistant".equalsIgnoreCase(role) ? "助手" : "用户";
            String clipped = clipForPrompt(content, 600);
            if (usedChars + clipped.length() > 1800) {
                clipped = clipForPrompt(clipped, Math.max(0, 1800 - usedChars));
            }
            if (clipped.isBlank()) {
                continue;
            }
            sb.append(label).append(": ").append(clipped).append("\n");
            usedChars += clipped.length();
            count += 1;
        }

        if (sb.length() == 0) {
            return "";
        }
        return "同一会话近期上下文，用于解析用户提到的“刚才、上面、继续、这个、上述”等指代。"
                + "请优先保持上下文连续，但不要在回答中复述这段说明：\n"
                + sb.toString().trim();
    }

    private String appendConversationReferenceContext(String baseSystemPrompt, String conversationReferenceContext) {
        String base = baseSystemPrompt != null ? baseSystemPrompt : "";
        if (conversationReferenceContext == null || conversationReferenceContext.isBlank()) {
            return base;
        }
        return base + "\n\n" + conversationReferenceContext;
    }

    private String clipForPrompt(String text, int maxChars) {
        if (text == null || maxChars <= 0) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private String extractContent(Map<String, Object> respMap) {
        if (respMap == null || respMap.isEmpty()) {
            return "";
        }

        Object topContent = respMap.get("content");
        if (topContent instanceof String s && !s.isBlank()) {
            return s;
        }

        // OpenAI/兼容格式: choices[0].message.content
        Object choicesObj = respMap.get("choices");
        if (choicesObj instanceof List<?> choices && !choices.isEmpty() && choices.get(0) instanceof Map<?, ?> choiceMap) {
            Object messageObj = choiceMap.get("message");
            if (messageObj instanceof Map<?, ?> messageMap) {
                Object contentObj = messageMap.get("content");
                if (contentObj instanceof String s && !s.isBlank()) {
                    return s;
                }
                String contentListText = extractTextFromContentParts(contentObj);
                if (!contentListText.isBlank()) {
                    return contentListText;
                }
                Object reasoningObj = messageMap.get("reasoning_content");
                if (reasoningObj instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
            Object textObj = choiceMap.get("text");
            if (textObj instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        // 某些 provider 封装在 data 内
        Object dataObj = respMap.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            Object answerObj = dataMap.get("answer");
            if (answerObj instanceof String s && !s.isBlank()) {
                return s;
            }
            Object messageObj = dataMap.get("message");
            if (messageObj instanceof String s && !s.isBlank()) {
                return s;
            }
            Object contentObj = dataMap.get("content");
            if (contentObj instanceof String s && !s.isBlank()) {
                return s;
            }
            String contentListText = extractTextFromContentParts(contentObj);
            if (!contentListText.isBlank()) {
                return contentListText;
            }
        }

        Object message = respMap.get("message");
        if (message instanceof String s && !s.isBlank()) {
            return s;
        }
        if (message instanceof Map<?, ?> messageMap) {
            Object contentObj = messageMap.get("content");
            if (contentObj instanceof String s && !s.isBlank()) {
                return s;
            }
            if (contentObj instanceof List<?> contentList) {
                StringBuilder sb = new StringBuilder();
                for (Object item : contentList) {
                    if (item instanceof Map<?, ?> partMap) {
                        Object textObj = partMap.get("text");
                        if (textObj instanceof String text && !text.isBlank()) {
                            if (sb.length() > 0) {
                                sb.append('\n');
                            }
                            sb.append(text);
                        }
                    } else if (item instanceof String text && !text.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(text);
                    }
                }
                if (!sb.isEmpty()) {
                    return sb.toString();
                }
            }
        }

        // Ollama 原生 generate 接口常见字段
        Object responseObj = respMap.get("response");
        if (responseObj instanceof String s && !s.isBlank()) {
            return s;
        }

        // 兜底兼容部分聚合网关字段
        Object outputTextObj = respMap.get("output_text");
        if (outputTextObj instanceof String s && !s.isBlank()) {
            return s;
        }

        Object textObj = respMap.get("text");
        if (textObj instanceof String s && !s.isBlank()) {
            return s;
        }

        return "";
    }

    private String extractTextFromContentParts(Object contentObj) {
        if (!(contentObj instanceof List<?> contentList)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object item : contentList) {
            if (item instanceof Map<?, ?> partMap) {
                Object textObj = partMap.get("text");
                if (textObj instanceof String text && !text.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(text);
                }
            } else if (item instanceof String text && !text.isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private String extractErrorText(Map<String, Object> respMap) {
        if (respMap == null || respMap.isEmpty()) {
            return "";
        }

        Object error = respMap.get("error");
        if (error != null && !String.valueOf(error).isBlank()) {
            return String.valueOf(error);
        }

        Object errorMessage = respMap.get("errorMessage");
        if (errorMessage != null && !String.valueOf(errorMessage).isBlank()) {
            return String.valueOf(errorMessage);
        }

        Object status = respMap.get("status");
        Object message = respMap.get("message");
        if (status != null && ("ERROR".equalsIgnoreCase(String.valueOf(status))
                || "FAILED".equalsIgnoreCase(String.valueOf(status)))) {
            if (message != null && !String.valueOf(message).isBlank()) {
                return String.valueOf(message);
            }
            Object topErrorMessage = respMap.get("errorMessage");
            if (topErrorMessage != null && !String.valueOf(topErrorMessage).isBlank()) {
                return String.valueOf(topErrorMessage);
            }
            return "上游模型服务返回错误状态";
        }

        Object dataObj = respMap.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            Object nestedError = dataMap.get("error");
            if (nestedError != null && !String.valueOf(nestedError).isBlank()) {
                return String.valueOf(nestedError);
            }
            Object nestedErrorMessage = dataMap.get("errorMessage");
            if (nestedErrorMessage != null && !String.valueOf(nestedErrorMessage).isBlank()) {
                return String.valueOf(nestedErrorMessage);
            }
        }

        return "";
    }

    @SuppressWarnings("unchecked")
    private RetrievalRuntimeConfig parseAgentRetrievalConfig(AgentMetadata metadata) {
        if (metadata == null || metadata.getParameters() == null || metadata.getParameters().isBlank()) {
            return RetrievalRuntimeConfig.empty();
        }
        try {
            Map<String, Object> params = objectMapper.readValue(metadata.getParameters(), Map.class);
            Object rawConfig = params.get("retrievalConfig");
            if (!(rawConfig instanceof Map<?, ?> rawMap)) {
                return RetrievalRuntimeConfig.empty();
            }
            Map<String, Object> config = (Map<String, Object>) rawMap;
            return new RetrievalRuntimeConfig(
                    asInteger(config.get("topK")),
                    asDouble(config.get("threshold")),
                    asDouble(config.get("alpha")),
                    asString(config.get("embeddingModel")),
                    asBoolean(config.get("enableRerank")),
                    asString(config.get("rerankModel")));
        } catch (Exception e) {
            log.warn("Failed to parse retrievalConfig for agent {}: {}",
                    metadata.getAgentId(), e.getMessage());
            return RetrievalRuntimeConfig.empty();
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text && !text.isBlank()) return Boolean.parseBoolean(text);
        return null;
    }

    private String asString(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "none".equalsIgnoreCase(text) ? null : text;
    }

    private record RetrievalRuntimeConfig(
            Integer topK,
            Double threshold,
            Double alpha,
            String embeddingModel,
            Boolean enableRerank,
            String rerankModel) {
        static RetrievalRuntimeConfig empty() {
            return new RetrievalRuntimeConfig(null, null, null, null, null, null);
        }
    }

    /**
     * 附加知识库到会话
     */
    @Transactional
    public void attachKnowledgeBase(String sessionId, String kbId) {
        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        
        List<String> kbIds = session.getAttachedKbIds();
        if (!kbIds.contains(kbId)) {
            kbIds.add(kbId);
            session.setAttachedKbIds(kbIds);
            sessionRepository.save(session);
        }
    }

    /**
     * 解绑知识库
     */
    @Transactional
    public void detachKnowledgeBase(String sessionId, String kbId) {
        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        
        session.getAttachedKbIds().remove(kbId);
        sessionRepository.save(session);
    }

    /**
     * 获取会话附加的知识库
     */
    public List<String> getAttachedKnowledgeBases(String sessionId) {
        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        
        return session.getAttachedKbIds();
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(String sessionId) {
        sessionRepository.deleteBySessionId(sessionId);
    }

    /**
     * 更新知识库的文档过滤配置
     */
    @Transactional
    public void updateKbDocFilters(String sessionId, Map<String, List<String>> kbDocFilters) {
        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        session.setKbDocFilters(kbDocFilters);
        sessionRepository.save(session);
    }
}
