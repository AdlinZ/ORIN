package com.adlin.orin.modules.runtime.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体运行日志
 * 记录 Dify Agent 的对话摘要或系统事件
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agent_logs")
public class AgentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String agentId;

    /**
     * 日志类型: CONVERSATION, SYSTEM, ERROR
     */
    private String type;

    /**
     * 日志级别: INFO, WARN, ERROR
     */
    private String level;

    /**
     * 日志内容 (摘要)
     */
    private String content;

    /**
     * 耗时 (ms)
     */
    private Integer duration;

    /**
     * 消耗 Token
     */
    private Integer tokens;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 状态 (SUCCESS, FAILED)
     */
    private String status;

    /**
     * 响应内容
     */
    @Column(columnDefinition = "TEXT")
    private String response;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
}
