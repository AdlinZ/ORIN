package com.adlin.orin.modules.conversation.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.conversation.dto.*;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.repository.AgentChatSessionRepository;
import com.adlin.orin.modules.knowledge.service.RetrievalService;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentChatSessionRepository sessionRepository;
    private final AgentManageService agentManageService;
    private final RetrievalService retrievalService;
    private final ObjectMapper objectMapper;

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
            List<Map<String, String>> messages = objectMapper.readValue(session.getHistory(), 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            result.put("messages", messages);
        } catch (Exception e) {
            result.put("messages", new ArrayList<>());
        }
        
        // 获取附加的知识库
        result.put("attachedKbs", session.getAttachedKbIds());
        
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
        List<Map<String, String>> messages;
        try {
            messages = objectMapper.readValue(session.getHistory(), 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            messages = new ArrayList<>();
        }
        
        // 添加用户消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", request.getMessage());
        messages.add(userMsg);
        
        // 知识库检索
        List<RetrievedChunk> retrievedChunks = new ArrayList<>();
        String context = "";
        
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
            try {
                for (String kbId : request.getKbIds()) {
                    List<VectorStoreProvider.SearchResult> results = 
                            retrievalService.hybridSearch(kbId, request.getMessage(), 5);
                    
                    for (VectorStoreProvider.SearchResult r : results) {
                        RetrievedChunk chunk = new RetrievedChunk();
                        chunk.setSource(r.getMetadata() != null ? 
                                r.getMetadata().getOrDefault("source", "未知来源") : "未知来源");
                        chunk.setContent(r.getContent());
                        chunk.setScore(r.getScore());
                        retrievedChunks.add(chunk);
                        
                        context += "\n\n" + r.getContent();
                    }
                }
            } catch (Exception e) {
                log.warn("知识库检索失败: {}", e.getMessage());
            }
        }
        
        // 调用智能体
        String aiResponse = callAgent(session.getAgentId(), request.getMessage(), context, messages);
        
        // 添加 AI 响应
        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", aiResponse);
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
        
        return response;
    }

    /**
     * 调用智能体（需要集成实际的 agent runtime）
     */
    private String callAgent(String agentId, String userMessage, String context, 
                             List<Map<String, String>> history) {
        try {
            AgentMetadata agent = agentManageService.getAgentById(agentId);
            
            // 构建 prompt
            StringBuilder prompt = new StringBuilder();
            if (context != null && !context.isEmpty()) {
                prompt.append("参考知识库内容:\n").append(context).append("\n\n");
            }
            prompt.append("用户: ").append(userMessage);
            
            // TODO: 实际调用 agent runtime
            // 这里先返回模拟响应
            log.info("调用智能体 {}: {}", agentId, prompt.substring(0, Math.min(100, prompt.length())));
            
            return "这是智能体 " + (agent != null ? agent.getName() : agentId) 
                    + " 的响应。\n\n知识库上下文: " 
                    + (context.isEmpty() ? "未附加" : "已参考")
                    + "\n\n实际调用需集成 Agent Runtime。";
                    
        } catch (Exception e) {
            log.error("调用智能体失败", e);
            return "智能体调用失败: " + e.getMessage();
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
}