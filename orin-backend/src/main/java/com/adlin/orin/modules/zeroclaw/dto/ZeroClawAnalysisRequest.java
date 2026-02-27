package com.adlin.orin.modules.zeroclaw.dto;

/**
 * ZeroClaw 分析请求 DTO
 */
public class ZeroClawAnalysisRequest {

    /**
     * 分析类型：ANOMALY_DIAGNOSIS, TREND_FORECAST, SYSTEM_OVERVIEW
     */
    private String analysisType;

    /**
     * 目标智能体 ID（可选，为空则分析全局）
     */
    private String agentId;

    /**
     * 数据时间范围开始（毫秒时间戳）
     */
    private Long startTime;

    /**
     * 数据时间范围结束（毫秒时间戳）
     */
    private Long endTime;

    /**
     * 分析上下文（额外提示词）
     */
    private String context;

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }

    public Long getEndTime() { return endTime; }
    public void setEndTime(Long endTime) { this.endTime = endTime; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
}
