package com.adlin.orin.modules.zeroclaw.repository;

import com.adlin.orin.modules.zeroclaw.entity.ZeroClawConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ZeroClaw 配置 Repository
 */
@Repository
public interface ZeroClawConfigRepository extends JpaRepository<ZeroClawConfig, String> {

    /**
     * 查找启用的配置
     */
    List<ZeroClawConfig> findByEnabledTrue();

    /**
     * 查找主配置（第一个启用的配置）
     */
    Optional<ZeroClawConfig> findFirstByEnabledTrue();
}
