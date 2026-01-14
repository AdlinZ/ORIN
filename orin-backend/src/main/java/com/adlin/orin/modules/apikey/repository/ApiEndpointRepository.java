package com.adlin.orin.modules.apikey.repository;

import com.adlin.orin.modules.apikey.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * API端点数据访问层
 */
@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, String> {

    /**
     * 根据路径和方法查找端点
     */
    Optional<ApiEndpoint> findByPathAndMethod(String path, String method);

    /**
     * 查找所有启用的端点
     */
    List<ApiEndpoint> findByEnabledTrue();

    /**
     * 根据路径查找端点
     */
    List<ApiEndpoint> findByPath(String path);

    /**
     * 根据权限标识查找端点
     */
    List<ApiEndpoint> findByPermissionRequired(String permission);

    /**
     * 检查路径和方法是否已存在
     */
    boolean existsByPathAndMethod(String path, String method);
}
