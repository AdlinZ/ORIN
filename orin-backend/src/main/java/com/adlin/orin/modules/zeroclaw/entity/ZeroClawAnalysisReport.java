package com.adlin.orin.modules.zeroclaw.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ZeroClaw 分析报告实体
 * 存储智能监控分析的结果
 */
@Entity
@Table(name = "zeroclaw_analysis_reports")
public class ZeroClawAnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 关联的智能体 ID
     */
    private String agentId;

    /**
     * 报告类型：SYSTEM_OVERVIEW, ANOMALY_DIAGNOSIS, TREND_FORECAST
     */
    @Column(nullable = false)
    private String reportType;

    /**
     * 报告标题
     */
    @Column(nullable = false)
    private String title;

    /**
     * 分析摘要
     */
    @Column(length = 2000)
    private String summary;

    /**
     * 详细分析内容 (JSON 格式存储)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * 根因分析
     */
    @Column(length = 1000)
    private String rootCause;

    /**
     * 建议措施
     */
    @Column(length = 1000)
    private String recommendations;

    /**
     * 严重程度：INFO, WARNING, CRITICAL
     */
    private String severity;

    /**
     * 分析开始时间
     */
    private LocalDateTime analysisStart;

    /**
     * 分析结束时间
     */
    private LocalDateTime analysisEnd;

    /**
     * 数据时间范围开始
     */
    private Long dataStartTime;

    /**
     * 数据时间范围结束
     */
    private Long dataEndTime;

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

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public String getRecommendations() { return recommendations; }
    public void setRecommendations(String recommendations) { this.recommendations = recommendations; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public LocalDateTime getAnalysisStart() { return analysisStart; }
    public void setAnalysisStart(LocalDateTime analysisStart) { this.analysisStart = analysisStart; }

    public LocalDateTime getAnalysisEnd() { return analysisEnd; }
    public void setAnalysisEnd(LocalDateTime analysisEnd) { this.analysisEnd = analysisEnd; }

    public Long getDataStartTime() { return dataStartTime; }
    public void setDataStartTime(Long dataStartTime) { this.dataStartTime = dataStartTime; }

    public Long getDataEndTime() { return dataEndTime; }
    public void setDataEndTime(Long dataEndTime) { this.dataEndTime = dataEndTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static ZeroClawAnalysisReportBuilder builder() {
        return new ZeroClawAnalysisReportBuilder();
    }

    public static class ZeroClawAnalysisReportBuilder {
        private String agentId;
        private String reportType;
        private String title;
        private String summary;
        private String details;
        private String rootCause;
        private String recommendations;
        private String severity;
        private LocalDateTime analysisStart;
        private LocalDateTime analysisEnd;
        private Long dataStartTime;
        private Long dataEndTime;

        public ZeroClawAnalysisReportBuilder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public ZeroClawAnalysisReportBuilder reportType(String reportType) {
            this.reportType = reportType;
            return this;
        }

        public ZeroClawAnalysisReportBuilder title(String title) {
            this.title = title;
            return this;
        }

        public ZeroClawAnalysisReportBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public ZeroClawAnalysisReportBuilder details(String details) {
            this.details = details;
            return this;
        }

        public ZeroClawAnalysisReportBuilder rootCause(String rootCause) {
            this.rootCause = rootCause;
            return this;
        }

        public ZeroClawAnalysisReportBuilder recommendations(String recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        public ZeroClawAnalysisReportBuilder severity(String severity) {
            this.severity = severity;
            return this;
        }

        public ZeroClawAnalysisReportBuilder analysisStart(LocalDateTime analysisStart) {
            this.analysisStart = analysisStart;
            return this;
        }

        public ZeroClawAnalysisReportBuilder analysisEnd(LocalDateTime analysisEnd) {
            this.analysisEnd = analysisEnd;
            return this;
        }

        public ZeroClawAnalysisReportBuilder dataStartTime(Long dataStartTime) {
            this.dataStartTime = dataStartTime;
            return this;
        }

        public ZeroClawAnalysisReportBuilder dataEndTime(Long dataEndTime) {
            this.dataEndTime = dataEndTime;
            return this;
        }

        public ZeroClawAnalysisReport build() {
            ZeroClawAnalysisReport report = new ZeroClawAnalysisReport();
            report.setAgentId(agentId);
            report.setReportType(reportType);
            report.setTitle(title);
            report.setSummary(summary);
            report.setDetails(details);
            report.setRootCause(rootCause);
            report.setRecommendations(recommendations);
            report.setSeverity(severity);
            report.setAnalysisStart(analysisStart);
            report.setAnalysisEnd(analysisEnd);
            report.setDataStartTime(dataStartTime);
            report.setDataEndTime(dataEndTime);
            return report;
        }
    }
}
