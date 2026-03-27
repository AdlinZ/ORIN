package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 系统配置 Repository
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, Long> {

    /**
     * 根据配置键查询配置
     */
    Optional<SystemConfigEntity> findByConfigKey(String configKey);
}