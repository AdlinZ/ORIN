package com.adlin.orin.modules.skill.service;

import com.adlin.orin.modules.skill.entity.McpService;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务接口
 */
public interface McpServiceService {

    /**
     * 获取所有 MCP 服务
     */
    List<McpService> getAllServices();

    /**
     * 根据 ID 获取 MCP 服务
     */
    McpService getServiceById(Long id);

    /**
     * 创建 MCP 服务
     */
    McpService createService(McpService service);

    /**
     * 更新 MCP 服务
     */
    McpService updateService(Long id, McpService service);

    /**
     * 删除 MCP 服务
     */
    void deleteService(Long id);

    /**
     * 测试 MCP 服务连接
     */
    Map<String, Object> testConnection(Long id);

    /**
     * 搜索 MCP 服务
     */
    List<McpService> searchServices(String keyword);

    /**
     * 根据服务类型获取服务列表
     */
    List<McpService> getServicesByType(McpService.McpType type);

    /**
     * 启用/禁用服务
     */
    McpService setServiceEnabled(Long id, boolean enabled);
}
