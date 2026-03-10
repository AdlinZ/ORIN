package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.ProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 供应商配置 Repository
 */
@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, String> {

    /**
     * 获取所有已启用的供应商，按显示顺序排序
     */
    @Query("SELECT p FROM ProviderConfig p WHERE p.enabled = true ORDER BY p.displayOrder ASC")
    List<ProviderConfig> findAllEnabledOrderByDisplayOrder();

    /**
     * 获取所有供应商，按显示顺序排序
     */
    @Query("SELECT p FROM ProviderConfig p ORDER BY p.displayOrder ASC")
    List<ProviderConfig> findAllOrderByDisplayOrder();
}
