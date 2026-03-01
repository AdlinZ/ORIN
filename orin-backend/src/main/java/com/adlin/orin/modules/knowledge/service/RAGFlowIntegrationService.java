package com.adlin.orin.modules.knowledge.service;

import java.util.List;
import java.util.Map;

/**
 * RAGFlow 集成服务接口
 */
public interface RAGFlowIntegrationService {

    /**
     * 测试 RAGFlow 连接
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @return 连接是否成功
     */
    boolean testConnection(String endpointUrl, String apiKey);

    /**
     * 创建知识库
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @param name 知识库名称
     * @param description 描述
     * @return 创建结果，包含知识库 ID
     */
    Map<String, Object> createKnowledgeBase(String endpointUrl, String apiKey, String name, String description);

    /**
     * 获取知识库列表
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @return 知识库列表
     */
    List<Map<String, Object>> listKnowledgeBases(String endpointUrl, String apiKey);

    /**
     * 获取知识库详情
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @param kbId 知识库 ID
     * @return 知识库详情
     */
    Map<String, Object> getKnowledgeBase(String endpointUrl, String apiKey, String kbId);

    /**
     * 删除知识库
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @param kbId 知识库 ID
     * @return 删除结果
     */
    Map<String, Object> deleteKnowledgeBase(String endpointUrl, String apiKey, String kbId);

    /**
     * 上传文档
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @param kbId 知识库 ID
     * @param fileName 文件名
     * @param fileContent 文件内容 (字节数组)
     * @return 上传结果
     */
    Map<String, Object> uploadDocument(String endpointUrl, String apiKey, String kbId,
                                       String fileName, byte[] fileContent);

    /**
     * 获取文档列表
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @param kbId 知识库 ID
     * @return 文档列表
     */
    List<Map<String, Object>> listDocuments(String endpointUrl, String apiKey, String kbId);

    /**
     * 删除文档
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @param docId 文档 ID
     * @return 删除结果
     */
    Map<String, Object> deleteDocument(String endpointUrl, String apiKey, String docId);

    /**
     * 检索测试
     * @param endpointUrl RAGFlow 端点
     * @param apiKey API Key
     * @param kbId 知识库 ID
     * @param query 查询语句
     * @param topK 返回数量
     * @return 检索结果
     */
    List<Map<String, Object>> retrievalTest(String endpointUrl, String apiKey, String kbId,
                                            String query, int topK);
}
