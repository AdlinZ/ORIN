package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 智能体健康状态聚合实体
 * 用于列表页快速展示
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体健康状态聚合实体
 * 用于列表页快速展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_health_status")
public class AgentHealthStatus {

    /**
     * 关联的智能体 ID (来自 Dify)
     * 同时作为主键，因为一个 Agent 只有一个实时状态
     */
    @Id
    private String agentId;

    /**
     * 智能体名称 (冗余字段，方便查询)
     */
    private String agentName;

    /**
     * 健康评分 (0-100)
     */
    private Integer healthScore;

    /**
     * 运行状态
     */
    @Enumerated(EnumType.STRING)
    private AgentStatus status;

    /**
     * 最后心跳/更新时间
     */
    private Long lastHeartbeat;

    /**
     * Provider 类型 (冗余自 Metadata，方便前端渲染)
     */
    private String providerType;

    /**
     * 运行模式 (冗余自 Metadata，方便前端渲染)
     */
    private String mode;

    /**
     * 模型名称 (冗余自 Metadata，方便前端渲染)
     */
    private String modelName;

    /**
     * 视图类型 (冗余自 Metadata，方便前端渲染)
     * 例如: CHAT, STT, TTI, WORKFLOW
     */
    private String viewType;
}
