package com.adlin.orin.modules.runtime.service;

import com.adlin.orin.modules.runtime.entity.AgentLog;

import java.util.List;

public interface RuntimeManageService {

    /**
     * 控制智能体运行状态
     * 
     * @param agentId 智能体ID
     * @param action  start / stop / restart
     */
    void controlAgent(String agentId, String action);

    /**
     * 获取智能体运行日志
     */
    List<AgentLog> getAgentLogs(String agentId);
}
