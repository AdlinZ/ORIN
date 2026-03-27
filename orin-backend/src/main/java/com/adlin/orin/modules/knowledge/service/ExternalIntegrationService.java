package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.ExternalIntegration;

import java.util.List;
import java.util.Map;

/**
 * 统一外部集成服务接口
 * 对 Dify、RAGFlow 等外部系统使用同一套接入抽象
 */
public interface ExternalIntegrationService {

    // ==================== 集成管理 ====================

    /**
     * 创建外部集成配置
     */
    ExternalIntegration createIntegration(ExternalIntegration integration);

    /**
     * 更新外部集成配置
     */
    ExternalIntegration updateIntegration(Long id, ExternalIntegration integration);

    /**
     * 删除外部集成配置
     */
    void deleteIntegration(Long id);

    /**
     * 获取外部集成配置
     */
    ExternalIntegration getIntegration(Long id);

    /**
     * 列出某知识库的所有外部集成
     */
    List<ExternalIntegration> listIntegrationsByKb(String knowledgeBaseId);

    /**
     * 列出所有外部集成
     */
    List<ExternalIntegration> listAllIntegrations();

    // ==================== 健康检查 ====================

    /**
     * 检查外部服务健康状态
     */
    ExternalIntegration.HealthStatus checkHealth(ExternalIntegration integration);

    /**
     * 批量检查所有集成健康状态
     */
    List<ExternalIntegration> checkAllHealth();

    // ==================== 同步操作 ====================

    /**
     * 从外部系统拉取数据
     */
    SyncResult pullFromExternal(Long integrationId);

    /**
     * 推送数据到外部系统
     */
    SyncResult pushToExternal(Long integrationId, List<Map<String, Object>> documents);

    /**
     * 执行双向同步
     */
    SyncResult syncBidirectional(Long integrationId);

    // ==================== 检索操作 ====================

    /**
     * 从外部系统检索
     */
    List<Map<String, Object>> retrieve(Long integrationId, String query, int topK);

    // ==================== 连接测试 ====================

    /**
     * 测试连接
     */
    ConnectionTestResult testConnection(ExternalIntegration integration);

    /**
     * 获取支持的能力列表
     */
    List<String> getSupportedCapabilities(String integrationType);

    /**
     * 同步结果
     */
    record SyncResult(
            boolean success,
            int addedCount,
            int updatedCount,
            int deletedCount,
            String message,
            String checkpoint
    ) {}

    /**
     * 连接测试结果
     */
    record ConnectionTestResult(
            boolean success,
            String message,
            Map<String, Object> details
    ) {}
}