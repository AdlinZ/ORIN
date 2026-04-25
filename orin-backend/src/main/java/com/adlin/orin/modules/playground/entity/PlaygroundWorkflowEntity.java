package com.adlin.orin.modules.playground.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "playground_workflows")
public class PlaygroundWorkflowEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String specialistAgentIdsJson;

    @Column(columnDefinition = "TEXT")
    private String routerPrompt;

    @Column(length = 32)
    private String executionMode;

    @Column(columnDefinition = "TEXT")
    private String dagSubtasksJson;

    private Integer agentMaxTokens = 2400;

    private Boolean finalizerEnabled = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSpecialistAgentIdsJson() {
        return specialistAgentIdsJson;
    }

    public void setSpecialistAgentIdsJson(String specialistAgentIdsJson) {
        this.specialistAgentIdsJson = specialistAgentIdsJson;
    }

    public String getRouterPrompt() {
        return routerPrompt;
    }

    public void setRouterPrompt(String routerPrompt) {
        this.routerPrompt = routerPrompt;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getDagSubtasksJson() {
        return dagSubtasksJson;
    }

    public void setDagSubtasksJson(String dagSubtasksJson) {
        this.dagSubtasksJson = dagSubtasksJson;
    }

    public Integer getAgentMaxTokens() {
        return agentMaxTokens;
    }

    public void setAgentMaxTokens(Integer agentMaxTokens) {
        this.agentMaxTokens = agentMaxTokens;
    }

    public Boolean getFinalizerEnabled() {
        return finalizerEnabled;
    }

    public void setFinalizerEnabled(Boolean finalizerEnabled) {
        this.finalizerEnabled = finalizerEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
