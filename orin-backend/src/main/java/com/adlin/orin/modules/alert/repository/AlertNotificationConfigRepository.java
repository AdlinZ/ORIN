package com.adlin.orin.modules.alert.repository;

import com.adlin.orin.modules.alert.entity.AlertNotificationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 告警通知配置 Repository
 */
@Repository
public interface AlertNotificationConfigRepository extends JpaRepository<AlertNotificationConfig, Long> {

    /**
     * 获取第一个配置（系统只有一条配置）
     */
    default Optional<AlertNotificationConfig> findFirstConfig() {
        return findAll().stream().findFirst();
    }
}
