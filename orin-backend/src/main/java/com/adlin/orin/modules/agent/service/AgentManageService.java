package com.adlin.orin.modules.agent.service;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;

import java.util.List;

public interface AgentManageService {

    /**
     * 接入新智能体 (Onboarding)
     * 
     * @param endpointUrl   Dify API 地址
     * @param apiKey        API Key
     * @param datasetApiKey 知识库 API Key (可选)
     * @return 接入成功的智能体元数据
     */
    AgentMetadata onboardAgent(String endpointUrl, String apiKey, String datasetApiKey);

    /**
     * 更新智能体配置
     * 
     * @param agentId     智能体ID
     * @param endpointUrl API 地址
     * @param apiKey      API Key
     * @param modelName   模型名称
     */
    void updateAgent(String agentId, com.adlin.orin.modules.agent.dto.AgentOnboardRequest request);

    /**
     * 与智能体对话
     * 
     * @param agentId 智能体ID
     * @param message 用户消息
     * @param file    上传的文件 (可选)
     * @return 响应内容
     */
    java.util.Optional<Object> chat(String agentId, String message,
            org.springframework.web.multipart.MultipartFile file);

    /**
     * 与智能体对话
     *
     * @param agentId 智能体ID
     * @param message 用户消息
     * @param fileId  上传的文件ID (可选)
     * @return 响应内容
     */
    java.util.Optional<Object> chat(String agentId, String message, String fileId);

    /**
     * 获取所有已纳管的智能体
     */
    List<AgentMetadata> getAllAgents();

    /**
     * 获取智能体接入配置
     */
    AgentAccessProfile getAgentAccessProfile(String agentId);

    /**
     * 获取指定智能体元数据
     */
    AgentMetadata getAgentMetadata(String agentId);

    /**
     * 删除智能体
     * 
     * @param agentId 智能体ID
     */
    void deleteAgent(String agentId);

    /**
     * 批量导出智能体配置
     * 
     * @param agentIds 智能体ID列表 (空则导出所有)
     * @return JSON文件字节数组
     */
    /**
     * 批量导出智能体配置
     * 
     * @param agentIds 智能体ID列表 (空则导出所有)
     * @return JSON文件字节数组
     */
    default byte[] batchExportAgents(List<String> agentIds) {
        throw new UnsupportedOperationException("Batch export not supported");
    }

    /**
     * 批量导入智能体配置
     * 
     * @param file JSON配置文件
     */
    default void batchImportAgents(org.springframework.web.multipart.MultipartFile file) {
        throw new UnsupportedOperationException("Batch import not supported");
    }
}
