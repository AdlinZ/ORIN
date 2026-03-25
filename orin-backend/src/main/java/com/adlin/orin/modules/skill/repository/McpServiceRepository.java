package com.adlin.orin.modules.skill.repository;

import com.adlin.orin.modules.skill.entity.McpService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MCP 服务仓储接口
 */
@Repository
public interface McpServiceRepository extends JpaRepository<McpService, Long> {

    /**
     * 根据服务名称模糊查询
     */
    List<McpService> findByNameContaining(String name);

    /**
     * 根据服务类型查询
     */
    List<McpService> findByType(McpService.McpType type);

    /**
     * 根据状态查询
     */
    List<McpService> findByStatus(McpService.McpStatus status);

    /**
     * 检查服务名称是否存在
     */
    boolean existsByName(String name);

    /**
     * 检查服务名称是否存在（排除指定 ID）
     */
    boolean existsByNameAndIdNot(String name, Long id);
}