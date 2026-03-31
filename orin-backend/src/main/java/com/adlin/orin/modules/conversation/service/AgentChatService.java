package com.adlin.orin.modules.conversation.service;

import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.conversation.dto.*;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.repository.AgentChatSessionRepository;
import com.adlin.orin.modules.conversation.tool.*;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentChatService {

    private final AgentChatSessionRepository sessionRepository;
    private final AgentManageService agentManageService;
    private final RetrievalService retrievalService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    private KbStructureTool kbStructureTool;
    private KbSearchTool kbSearchTool;
    private KbRetrieveTool kbRetrieveTool;

    public AgentChatService(
            AgentChatSessionRepository sessionRepository,
            AgentManageService agentManageService,
            RetrievalService retrievalService,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeDocumentRepository documentRepository,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.agentManageService = agentManageService;
        this.retrievalService = retrievalService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
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
        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));
        
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
                .kbDocFilters(request.getKbDocFilters())
                .traces(new ArrayList<>())
                .sharedState(new HashMap<>())
                .retrievedChunks(new ArrayList<>())
                .build();

        // 执行工具链
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
            try {
                toolCtx.addTrace(kbStructureTool.execute(toolCtx));
                toolCtx.addTrace(kbSearchTool.execute(toolCtx));
                toolCtx.addTrace(kbRetrieveTool.execute(toolCtx));
            } catch (Exception e) {
                log.warn("工具链执行失败: {}", e.getMessage());
                toolCtx.addTrace(ChatMessageResponse.ToolTrace.builder()
                        .type("KB_PIPELINE")
                        .kbId("multiple")
                        .message("知识库检索链路异常，已跳过知识上下文")
                        .status("error")
                        .durationMs(0L)
                        .detail(Map.of("error", e.getMessage()))
                        .build());
            }
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

        // 调用智能体
        log.info("检索上下文内容: retrievedChunks.size={}, context长度={}, context前200字={}",
                retrievedChunks.size(),
                context != null ? context.length() : 0,
                context != null && context.length() > 200 ? context.substring(0, 200) : context);

        // 附加了知识库但未检索到任何内容时，给前端一个明确提醒（避免“静默失败”）。
        if (request.getKbIds() != null && !request.getKbIds().isEmpty() && retrievedChunks.isEmpty()) {
            toolCtx.addTrace(ChatMessageResponse.ToolTrace.builder()
                    .type("KB_HINT")
                    .kbId("multiple")
                    .message("未检索到可用知识：可能是向量服务不可用、文档未解析/未向量化，或查询词未命中。")
                    .status("warning")
                    .durationMs(0L)
                    .detail(Map.of(
                            "kbIds", request.getKbIds(),
                            "kbDocFilters", request.getKbDocFilters() != null ? request.getKbDocFilters() : Collections.emptyMap(),
                            "retrievedCount", 0))
                    .build());
        }

        Map<String, Object> agentResult = callAgent(session.getAgentId(), request.getMessage(), context, messages);
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

        return response;
    }

    /**
     * 调用智能体，返回内容和usage信息
     */
    private Map<String, Object> callAgent(String agentId, String userMessage, String context,
                             List<Map<String, Object>> history) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", "");
        result.put("promptTokens", 0);
        result.put("completionTokens", 0);
        result.put("model", "");
        result.put("provider", "");

        try {
            // 构建完整的消息，包含知识库上下文
            String fullMessage = userMessage;
            if (context != null && !context.isEmpty()) {
                fullMessage = "参考知识库内容:\n" + context + "\n\n用户: " + userMessage;
            }

            log.info("调用智能体 {}，消息长度: {}", agentId, fullMessage.length());

            // 调用 AgentManageService 的 chat 方法
            Optional<Object> response = agentManageService.chat(agentId, fullMessage, (String) null);

            if (response.isPresent()) {
                Object resp = response.get();
                if (resp instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> respMap = (Map<String, Object>) resp;
                    String content = extractContent(respMap);
                    if (content == null || content.isBlank()) {
                        String errorText = extractErrorText(respMap);
                        if (errorText != null && !errorText.isBlank()) {
                            content = "模型调用失败: " + errorText;
                        }
                    }
                    if (content == null || content.isBlank()) {
                        content = "检索流程已完成，但模型未返回正文。";
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
        if (status != null && "ERROR".equalsIgnoreCase(String.valueOf(status))) {
            if (message != null && !String.valueOf(message).isBlank()) {
                return String.valueOf(message);
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
