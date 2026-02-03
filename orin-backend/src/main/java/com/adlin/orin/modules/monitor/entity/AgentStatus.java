package com.adlin.orin.modules.monitor.entity;

/**
 * 智能体运行状态
 */
public enum AgentStatus {
    RUNNING,
    STOPPED,
    HIGH_LOAD, // 高负载
    ERROR, // 异常
    UNKNOWN // 未知
}
