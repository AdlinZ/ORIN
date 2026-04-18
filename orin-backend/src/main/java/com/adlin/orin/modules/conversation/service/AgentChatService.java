package com.adlin.orin.modules.conversation.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.conversation.dto.*;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.repository.AgentChatSessionRepository;
import com.adlin.orin.modules.conversation.strategy.ToolCallingCapabilityDetector;
import com.adlin.orin.modules.conversation.strategy.ToolCallingKbStrategy;
import com.adlin.orin.modules.conversation.tool.*;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
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
    private final MetaKnowledgeService metaKnowledgeService;
    private final RetrievalService retrievalService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final McpServiceRepository mcpServiceRepository;
    private final OllamaIntegrationService ollamaIntegrationService;
    private final ToolCallingCapabilityDetector toolCallingDetector;
    private final ObjectMapper objectMapper;

    private KbStructureTool kbStructureTool;
    private KbSearchTool kbSearchTool;
    private KbRetrieveTool kbRetrieveTool;

    public AgentChatService(
            AgentChatSessionRepository sessionRepository,
            AgentManageService agentManageService,
            MetaKnowledgeService metaKnowledgeService,
            RetrievalService retrievalService,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository documentRepository,
            McpServiceRepository mcpServiceRepository,
            OllamaIntegrationService ollamaIntegrationService,
            ToolCallingCapabilityDetector toolCallingDetector,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.agentManageService = agentManageService;
        this.metaKnowledgeService = metaKnowledgeService;
        this.retrievalService = retrievalService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.mcpServiceRepository = mcpServiceRepository;
        this.ollamaIntegrationService = ollamaIntegrationService;
        this.toolCallingDetector = toolCallingDetector;
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
        userMsg.put("createdAt", java.time.LocalDateTime.now().toString());
        messages.add(userMsg);

        // 构建工具执行上下文
        ToolExecutionContext toolCtx = ToolExecutionContext.builder()
                .sessionId(sessionId)
                .query(request.getMessage())
                .kbIds(request.getKbIds())
                .mcpIds(request.getMcpIds())
                .kbDocFilters(request.getKbDocFilters())
                .traces(new ArrayList<>())
                .sharedState(new HashMap<>())
                .retrievedChunks(new ArrayList<>())
                .build();

        // 执行工具链
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
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
        if (request.getMcpIds() != null && !request.getMcpIds().isEmpty()) {
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
        if (!useToolCalling && request.getKbIds() != null && !request.getKbIds().isEmpty() && retrievedChunks.isEmpty()) {
            ChatMessageResponse.ToolTrace hintTrace = ChatMessageResponse.ToolTrace.builder()
                    .type("KB_HINT")
                    .kbId("multiple")
                    .message("未检索到可用知识：可能是向量服务不可用、文档未解析/未向量化，或查询词未命中。")
                    .status("warning")
                    .durationMs(0L)
                    .detail(Map.of(
                            "kbIds", request.getKbIds(),
                            "kbDocFilters", request.getKbDocFilters() != null ? request.getKbDocFilters() : Collections.emptyMap(),
                            "retrievedCount", 0))
                    .build();
            toolCtx.addTrace(hintTrace);
            emitEvent(eventPublisher, "trace", hintTrace);
        }

        emitEvent(eventPublisher, "progress", Map.of(
                "step", "MODEL_CALL",
                "message", "正在生成回复..."));
        Map<String, Object> agentResult = callAgent(
                session.getAgentId(),
                request.getMessage(),
                context,
                toolCtx,
                eventPublisher,
                useToolCalling);
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
    private Map<String, Object> callAgent(String agentId, String userMessage, String context,
                             ToolExecutionContext toolCtx,
                             BiConsumer<String, Object> eventPublisher,
                             boolean useToolCalling) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", "");
        result.put("promptTokens", 0);
        result.put("completionTokens", 0);
        result.put("model", "");
        result.put("provider", "");

        try {
            AgentMetadata metadata = agentManageService.getAgentMetadata(agentId);
            AgentAccessProfile profile = agentManageService.getAgentAccessProfile(agentId);

            // ── 支持 tool calling 的模型：让模型自主决定检索还是读全文 ──────────
            if (toolCtx != null && toolCtx.getKbIds() != null && !toolCtx.getKbIds().isEmpty()
                    && useToolCalling) {
                String baseSystemPrompt = metaKnowledgeService.assembleSystemPrompt(agentId);
                double temperature = metadata != null && metadata.getTemperature() != null
                        ? metadata.getTemperature() : 0.7;
                int maxTokens = metadata != null && metadata.getMaxTokens() != null
                        ? metadata.getMaxTokens() : 2048;

                ToolCallingKbStrategy strategy = new ToolCallingKbStrategy(
                        ollamaIntegrationService, retrievalService, documentRepository, objectMapper);

                log.info("tool-calling RAG: agentId={}, kbIds={}", agentId, toolCtx.getKbIds());
                return strategy.execute(
                        profile.getEndpointUrl(), profile.getApiKey(),
                        metadata != null ? metadata.getModelName() : null,
                        baseSystemPrompt, userMessage, toolCtx.getKbIds(),
                        temperature, maxTokens,
                        trace -> {
                            toolCtx.addTrace(trace);
                            emitEvent(eventPublisher, "trace", trace);
                        });
            }

            // ── 云端 provider：context injection（将检索结果注入 system prompt）──
            String baseSystemPrompt = metaKnowledgeService.assembleSystemPrompt(agentId);
            String kbStructurePreamble = buildKbStructurePreamble(toolCtx);
            String mcpContext = toolCtx != null ? (String) toolCtx.getSharedState("mcpContext") : null;

            StringBuilder systemSuffix = new StringBuilder();
            if (kbStructurePreamble != null && !kbStructurePreamble.isBlank()) {
                systemSuffix.append("\n\n").append(kbStructurePreamble);
            }
            if (context != null && !context.isEmpty()) {
                systemSuffix.append("\n\n以下是与用户问题相关的知识库检索内容，请直接基于此回答，无需说明内容来源：\n").append(context);
            }
            if (mcpContext != null && !mcpContext.isBlank()) {
                systemSuffix.append("\n\n可用 MCP 服务:\n").append(mcpContext);
            }

            String extendedSystemPrompt = systemSuffix.length() > 0
                    ? baseSystemPrompt + systemSuffix
                    : null;

            log.info("云端 provider context-injection RAG: agentId={}, systemSuffix长度={}, userMessage长度={}",
                    agentId, systemSuffix.length(), userMessage.length());

            // 调用 AgentManageService 的 chat 方法；有知识库/MCP 上下文时通过 overrideSystemPrompt 注入
            Optional<Object> response = extendedSystemPrompt != null
                    ? agentManageService.chat(agentId, userMessage, null, extendedSystemPrompt)
                    : agentManageService.chat(agentId, userMessage, (String) null);

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
                            Optional<Object> fallbackResp = agentManageService.chat(agentId, userMessage, (String) null);
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
                    result.put("content", content);
                    result.put("promptTokens", respMap.getOrDefault("promptTokens", 0));
                    result.put("completionTokens", respMap.getOrDefault("completionTokens", 0));
                    result.put("model", respMap.getOrDefault("model", ""));
                    result.put("provider", respMap.getOrDefault("provider", ""));
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

        return "";
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
