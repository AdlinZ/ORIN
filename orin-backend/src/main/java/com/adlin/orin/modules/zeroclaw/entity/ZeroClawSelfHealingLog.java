package com.adlin.orin.modules.zeroclaw.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ZeroClaw 主动维护操作记录实体
 * 记录自动化故障排除的执行历史和结果
 */
@Entity
@Table(name = "zeroclaw_self_healing_logs")
public class ZeroClawSelfHealingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 操作类型：CLEAR_LOGS, RESTART_PROCESS, CLEANUP_CACHE, SCALE_RESOURCE
     */
    @Column(nullable = false)
    private String actionType;

    /**
     * 目标资源标识（如进程名、服务名）
     */
    private String targetResource;

    /**
     * 触发原因
     */
    @Column(length = 1000)
    private String triggerReason;

    /**
     * 操作执行状态：PENDING, RUNNING, SUCCESS, FAILED, ROLLED_BACK
     */
    @Column(nullable = false)
    private String status = "PENDING";

    /**
     * 执行详情 (JSON 格式)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String executionDetails;

    /**
     * 错误信息
     */
    @Column(length = 2000)
    private String errorMessage;

    /**
     * 执行前状态快照
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String beforeSnapshot;

    /**
     * 执行后状态快照
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String afterSnapshot;

    /**
     * 执行开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 执行完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 是否自动执行（非人工触发）
     */
    private Boolean autoExecuted = true;

    /**
     * 执行人（如果是手动触发）
     */
    private String executedBy;

    /**
     * 创建时间
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getTargetResource() { return targetResource; }
    public void setTargetResource(String targetResource) { this.targetResource = targetResource; }

    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExecutionDetails() { return executionDetails; }
    public void setExecutionDetails(String executionDetails) { this.executionDetails = executionDetails; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(String beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }

    public String getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(String afterSnapshot) { this.afterSnapshot = afterSnapshot; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Boolean getAutoExecuted() { return autoExecuted; }
    public void setAutoExecuted(Boolean autoExecuted) { this.autoExecuted = autoExecuted; }

    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static ZeroClawSelfHealingLogBuilder builder() {
        return new ZeroClawSelfHealingLogBuilder();
    }

    public static class ZeroClawSelfHealingLogBuilder {
        private String actionType;
        private String targetResource;
        private String triggerReason;
        private String status = "PENDING";
        private String executionDetails;
        private String errorMessage;
        private String beforeSnapshot;
        private String afterSnapshot;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Boolean autoExecuted = true;
        private String executedBy;

        public ZeroClawSelfHealingLogBuilder actionType(String actionType) {
            this.actionType = actionType;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder targetResource(String targetResource) {
            this.targetResource = targetResource;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder triggerReason(String triggerReason) {
            this.triggerReason = triggerReason;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder status(String status) {
            this.status = status;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder executionDetails(String executionDetails) {
            this.executionDetails = executionDetails;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder beforeSnapshot(String beforeSnapshot) {
            this.beforeSnapshot = beforeSnapshot;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder afterSnapshot(String afterSnapshot) {
            this.afterSnapshot = afterSnapshot;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder startedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder autoExecuted(Boolean autoExecuted) {
            this.autoExecuted = autoExecuted;
            return this;
        }

        public ZeroClawSelfHealingLogBuilder executedBy(String executedBy) {
            this.executedBy = executedBy;
            return this;
        }

        public ZeroClawSelfHealingLog build() {
            ZeroClawSelfHealingLog log = new ZeroClawSelfHealingLog();
            log.setActionType(actionType);
            log.setTargetResource(targetResource);
            log.setTriggerReason(triggerReason);
            log.setStatus(status);
            log.setExecutionDetails(executionDetails);
            log.setErrorMessage(errorMessage);
            log.setBeforeSnapshot(beforeSnapshot);
            log.setAfterSnapshot(afterSnapshot);
            log.setStartedAt(startedAt);
            log.setCompletedAt(completedAt);
            log.setAutoExecuted(autoExecuted);
            log.setExecutedBy(executedBy);
            return log;
        }
    }
}
