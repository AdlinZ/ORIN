package com.adlin.orin.modules.conversation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 智能体会话实体
 * 支持知识库附加的对话会话
 */
@Entity
@Table(name = "agent_chat_session")
public class AgentChatSession {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String agentId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String history;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(name = "attached_kb_ids", columnDefinition = "JSON")
    private String attachedKbIdsJson;

    @Column(name = "kb_doc_filters", columnDefinition = "JSON")
    private String kbDocFiltersJson;

    // Transient cache for in-memory access
    @Transient
    private List<String> attachedKbIds = new ArrayList<>();

    @Transient
    private Map<String, List<String>> kbDocFilters = new HashMap<>();

    @PostLoad
    private void loadJsonFields() {
        try {
            if (attachedKbIdsJson != null && !attachedKbIdsJson.isEmpty()) {
                attachedKbIds = objectMapper.readValue(attachedKbIdsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
            if (kbDocFiltersJson != null && !kbDocFiltersJson.isEmpty()) {
                kbDocFilters = objectMapper.readValue(kbDocFiltersJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, List.class));
            }
        } catch (JsonProcessingException e) {
            attachedKbIds = new ArrayList<>();
            kbDocFilters = new HashMap<>();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHistory() {
        return history;
    }

    public void setHistory(String history) {
        this.history = history;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getAttachedKbIds() {
        return attachedKbIds;
    }

    public void setAttachedKbIds(List<String> attachedKbIds) {
        this.attachedKbIds = attachedKbIds;
        try {
            this.attachedKbIdsJson = objectMapper.writeValueAsString(attachedKbIds);
        } catch (JsonProcessingException e) {
            this.attachedKbIdsJson = "[]";
        }
    }

    public Map<String, List<String>> getKbDocFilters() {
        return kbDocFilters;
    }

    public void setKbDocFilters(Map<String, List<String>> kbDocFilters) {
        this.kbDocFilters = kbDocFilters;
        try {
            this.kbDocFiltersJson = objectMapper.writeValueAsString(kbDocFilters);
        } catch (JsonProcessingException e) {
            this.kbDocFiltersJson = "{}";
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        syncJsonFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        syncJsonFields();
    }

    private void syncJsonFields() {
        try {
            if (attachedKbIds != null) {
                this.attachedKbIdsJson = objectMapper.writeValueAsString(attachedKbIds);
            }
            if (kbDocFilters != null) {
                this.kbDocFiltersJson = objectMapper.writeValueAsString(kbDocFilters);
            }
        } catch (JsonProcessingException e) {
            // ignore
        }
    }
}